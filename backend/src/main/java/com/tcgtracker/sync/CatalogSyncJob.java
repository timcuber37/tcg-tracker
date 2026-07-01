package com.tcgtracker.sync;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.tcgtracker.external.PokeWalletClient;

/**
 * Standalone catalog sync (profile `sync`). Pulls English XY-era+ sets from
 * PokéWallet once per day and upserts them into the read models via
 * {@link CatalogRefreshService}. Ports sync/api_sync.py.
 *
 * Rate-limit aware: a fixed delay between set fetches keeps within the free tier.
 */
@Component
@Profile("sync")
public class CatalogSyncJob {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncJob.class);

    private static final long SYNC_DELAY_MS = 40_000;             // between set requests
    // XY era proper starts Nov 2013, but the XY Promos set (set_id 1451) released
    // 17 Aug 2013 — the earliest XY-era product. This cutoff admits it while still
    // excluding the prior BW-era Plasma Blast (14 Aug 2013).
    private static final LocalDate XY_ERA_START = LocalDate.of(2013, 8, 17);
    private static final Pattern DATE_RE =
        Pattern.compile("(\\d{1,2})(?:st|nd|rd|th)?\\s+(\\w+),?\\s+(\\d{4})");

    private final PokeWalletClient pokewallet;
    private final CatalogRefreshService catalogRefresh;

    // Optional comma-separated set_ids to sync exactly (bypasses the era filter).
    // Used for targeted backfills, e.g. SYNC_ONLY_SETS=23651,24073,24269.
    @Value("${sync.only-sets:}")
    private String onlySets;

    public CatalogSyncJob(PokeWalletClient pokewallet, CatalogRefreshService catalogRefresh) {
        this.pokewallet = pokewallet;
        this.catalogRefresh = catalogRefresh;
    }

    @Scheduled(initialDelayString = "${sync.initial-delay-ms:10000}",
               fixedDelayString = "${sync.interval-ms:86400000}")
    public void runSyncPass() {
        log.info("Starting catalog sync pass...");
        List<JsonNode> allSets = pokewallet.getAllSets();

        List<JsonNode> targets;
        if (onlySets != null && !onlySets.isBlank()) {
            java.util.Set<String> ids = java.util.Set.of(onlySets.split("\\s*,\\s*"));
            targets = allSets.stream().filter(s -> ids.contains(s.path("set_id").asText())).toList();
            log.info("Targeted sync of {} requested set(s): {}", targets.size(), onlySets);
        } else {
            targets = allSets.stream().filter(CatalogSyncJob::isXyEraOrNewer).toList();
            log.info("Found {} sets — syncing {} English XY-era+ sets", allSets.size(), targets.size());
        }

        int total = 0;
        for (int i = 0; i < targets.size(); i++) {
            total += catalogRefresh.refreshSet(targets.get(i)).cardsUpdated();
            if (i < targets.size() - 1) {
                sleep(SYNC_DELAY_MS);
            }
        }
        log.info("Sync pass complete. Total cards synced: {}", total);
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
