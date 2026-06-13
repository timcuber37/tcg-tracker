package com.pokecollect.sync;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.fasterxml.jackson.databind.JsonNode;
import com.pokecollect.external.PokeWalletClient;

/**
 * Standalone catalog sync (profile `sync`). Pulls English XY-era+ sets from
 * PokéWallet once per day and upserts them into the Cassandra cards_by_set read
 * model and the Postgres catalog. Ports sync/api_sync.py.
 *
 * Rate-limit aware: a fixed delay between set fetches keeps within the free tier.
 */
@Component
@Profile("sync")
public class CatalogSyncJob {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncJob.class);

    private static final long SYNC_DELAY_MS = 40_000;             // between set requests
    private static final LocalDate XY_ERA_START = LocalDate.of(2013, 11, 1);
    private static final Pattern DATE_RE =
        Pattern.compile("(\\d{1,2})(?:st|nd|rd|th)?\\s+(\\w+),?\\s+(\\d{4})");

    private static final String CASS_INSERT_PRICED =
        "INSERT INTO cards_by_set (set_name, card_id, card_name, rarity, card_type, market_price_usd, pokewallet_id) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String CASS_INSERT_NOPRICE =
        "INSERT INTO cards_by_set (set_name, card_id, card_name, rarity, card_type, pokewallet_id) " +
        "VALUES (?, ?, ?, ?, ?, ?)";
    private static final String PG_UPSERT =
        "INSERT INTO catalog_embeddings (pokewallet_id, card_name, set_name, rarity, card_type, market_price_usd) " +
        "VALUES (?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (pokewallet_id) DO UPDATE SET " +
        "  market_price_usd = COALESCE(EXCLUDED.market_price_usd, catalog_embeddings.market_price_usd), " +
        "  updated_at = NOW()";

    private final PokeWalletClient pokewallet;
    private final CqlSession cql;
    private final JdbcTemplate pg;

    public CatalogSyncJob(PokeWalletClient pokewallet, CqlSession cql,
                          @Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.pokewallet = pokewallet;
        this.cql = cql;
        this.pg = postgresJdbcTemplate;
    }

    @Scheduled(initialDelayString = "${sync.initial-delay-ms:10000}",
               fixedDelayString = "${sync.interval-ms:86400000}")
    public void runSyncPass() {
        log.info("Starting catalog sync pass...");
        List<JsonNode> allSets = pokewallet.getAllSets();
        List<JsonNode> targets = allSets.stream().filter(CatalogSyncJob::isXyEraOrNewer).toList();
        log.info("Found {} sets — syncing {} English XY-era+ sets", allSets.size(), targets.size());

        int total = 0;
        for (int i = 0; i < targets.size(); i++) {
            total += syncSet(targets.get(i));
            if (i < targets.size() - 1) {
                sleep(SYNC_DELAY_MS);
            }
        }
        log.info("Sync pass complete. Total cards synced: {}", total);
    }

    private int syncSet(JsonNode setInfo) {
        String setId = setInfo.path("set_id").asText(null);
        String setName = setInfo.path("name").asText(setId);
        if (setId == null) {
            return 0;
        }

        int page = 1;
        int synced = 0;
        while (true) {
            JsonNode data = pokewallet.getSetCards(setId, page, 200);
            JsonNode cards = data == null ? null : data.get("cards");
            if (cards == null || !cards.isArray() || cards.isEmpty()) {
                break;
            }

            for (JsonNode card : cards) {
                JsonNode info = card.path("card_info");
                String cardId = card.path("id").asText("");
                String cardName = info.path("name").asText(info.path("clean_name").asText(""));
                String rarity = info.path("rarity").asText("Unknown");
                String cardType = info.path("card_type").asText("Unknown");
                Double price = pokewallet.extractTcgPlayerPrice(card);
                if (cardId.isEmpty() || cardName.isEmpty()) {
                    continue;
                }

                if (price != null) {
                    cql.execute(SimpleStatement.newInstance(CASS_INSERT_PRICED,
                        setName, cardId, cardName, rarity, cardType, BigDecimal.valueOf(price), cardId));
                } else {
                    cql.execute(SimpleStatement.newInstance(CASS_INSERT_NOPRICE,
                        setName, cardId, cardName, rarity, cardType, cardId));
                }
                pg.update(PG_UPSERT, cardId, cardName, setName, rarity, cardType, price);
                synced++;
            }

            JsonNode pagination = data.path("pagination");
            if (page >= pagination.path("total_pages").asInt(1)) {
                break;
            }
            page++;
        }
        log.info("Synced {} cards from set '{}'", synced, setName);
        return synced;
    }

    static boolean isXyEraOrNewer(JsonNode setInfo) {
        if (!"eng".equalsIgnoreCase(setInfo.path("language").asText(""))) {
            return false;
        }
        LocalDate release = parseReleaseDate(setInfo.path("release_date").asText(null));
        return release != null && !release.isBefore(XY_ERA_START);
    }

    static LocalDate parseReleaseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = DATE_RE.matcher(raw.strip());
        if (!m.lookingAt()) {
            return null;
        }
        try {
            int day = Integer.parseInt(m.group(1));
            Month month = MONTHS.getOrDefault(m.group(2), null);
            int year = Integer.parseInt(m.group(3));
            return month == null ? null : LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private static final Map<String, Month> MONTHS = Map.ofEntries(
        Map.entry("January", Month.JANUARY), Map.entry("February", Month.FEBRUARY),
        Map.entry("March", Month.MARCH), Map.entry("April", Month.APRIL),
        Map.entry("May", Month.MAY), Map.entry("June", Month.JUNE),
        Map.entry("July", Month.JULY), Map.entry("August", Month.AUGUST),
        Map.entry("September", Month.SEPTEMBER), Map.entry("October", Month.OCTOBER),
        Map.entry("November", Month.NOVEMBER), Map.entry("December", Month.DECEMBER));

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
