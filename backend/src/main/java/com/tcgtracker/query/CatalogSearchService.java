package com.tcgtracker.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import com.tcgtracker.query.dto.CardDto;
import com.tcgtracker.query.dto.SearchResponse;

/**
 * Read-side catalog search against the Supabase Postgres {@code catalog_embeddings}
 * table. Pure reads — never touches MySQL or Kafka. Ports queries/postgres_search.py.
 */
@Service
public class CatalogSearchService {

    // 24 divides evenly by the 4-card grid rows, so the last row is never partial.
    public static final int PAGE_SIZE = 24;

    private final JdbcTemplate pg;

    public CatalogSearchService(@Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.pg = postgresJdbcTemplate;
    }

    private static final RowMapper<CardDto> CARD_MAPPER = (rs, n) -> {
        Object price = rs.getObject("market_price_usd");
        return new CardDto(
            rs.getString("pokewallet_id"),
            rs.getString("card_name"),
            rs.getString("set_name"),
            rs.getString("rarity"),
            rs.getString("card_type"),
            price == null ? null : ((Number) price).doubleValue()
        );
    };

    /** Paginated search by card name and/or set. Energy cards are excluded. */
    public SearchResponse search(String query, String setName, int page) {
        if ((query == null || query.isBlank()) && (setName == null || setName.isBlank())) {
            return new SearchResponse(List.of(), 0, page, 0, PAGE_SIZE);
        }

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        conditions.add("card_type NOT ILIKE 'Energy%'");

        if (query != null && !query.isBlank()) {
            conditions.add("card_name ILIKE ?");
            params.add("%" + query + "%");
        }
        if (setName != null && !setName.isBlank()) {
            conditions.add("set_name = ?");
            params.add(setName);
        }

        String where = String.join(" AND ", conditions);
        int offset = Math.max(0, (page - 1) * PAGE_SIZE);

        Long total = pg.queryForObject(
            "SELECT COUNT(*) FROM catalog_embeddings WHERE " + where,
            Long.class, params.toArray());
        long totalCount = total == null ? 0 : total;

        List<Object> pagedParams = new ArrayList<>(params);
        pagedParams.add(PAGE_SIZE);
        pagedParams.add(offset);

        List<CardDto> results = pg.query(
            "SELECT pokewallet_id, card_name, set_name, rarity, card_type, market_price_usd " +
            "FROM catalog_embeddings WHERE " + where + " " +
            "ORDER BY market_price_usd DESC NULLS LAST, card_name LIMIT ? OFFSET ?",
            CARD_MAPPER, pagedParams.toArray());

        int totalPages = (int) ((totalCount + PAGE_SIZE - 1) / PAGE_SIZE);
        return new SearchResponse(results, totalCount, page, totalPages, PAGE_SIZE);
    }

    /** Distinct set names, optionally limited to sets containing matches for {@code query}. */
    public List<String> setNames(String query) {
        if (query != null && !query.isBlank()) {
            return pg.queryForList(
                "SELECT DISTINCT set_name FROM catalog_embeddings " +
                "WHERE card_type NOT ILIKE 'Energy%' AND card_name ILIKE ? ORDER BY set_name",
                String.class, "%" + query + "%");
        }
        return pg.queryForList(
            "SELECT DISTINCT set_name FROM catalog_embeddings ORDER BY set_name",
            String.class);
    }

    /** Catalog detail for a single card, used by the add-copy command. Null if not found. */
    public CatalogCard cardByPokewalletId(String pokewalletId) {
        List<CatalogCard> rows = pg.query(
            "SELECT card_name, set_name, rarity, card_type, market_price_usd " +
            "FROM catalog_embeddings WHERE pokewallet_id = ?",
            (rs, n) -> {
                Object price = rs.getObject("market_price_usd");
                return new CatalogCard(
                    rs.getString("card_name"), rs.getString("set_name"),
                    rs.getString("rarity"), rs.getString("card_type"),
                    price == null ? null : ((Number) price).doubleValue());
            },
            pokewalletId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public record CatalogCard(String cardName, String setName, String rarity,
                              String cardType, Double marketPriceUsd) {}

    /** Map of {pokewallet_id -> market_price_usd} for the given card IDs (price overlay). */
    public Map<String, Double> currentPrices(List<String> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> prices = new HashMap<>();
        pg.query(
            "SELECT pokewallet_id, market_price_usd FROM catalog_embeddings WHERE pokewallet_id = ANY(?)",
            ps -> ps.setArray(1, ps.getConnection().createArrayOf("text", cardIds.toArray())),
            rs -> {
                Object p = rs.getObject("market_price_usd");
                prices.put(rs.getString("pokewallet_id"), p == null ? null : ((Number) p).doubleValue());
            });
        return prices;
    }

    /** Random sample of high-rarity card IDs for the home-page rain animation. */
    public List<String> rareCardIds(int limit) {
        return pg.queryForList(
            "SELECT pokewallet_id FROM catalog_embeddings " +
            "WHERE rarity ILIKE ANY(ARRAY[" +
            "  '%Special Illustration Rare%'," +
            "  '%Hyper Rare%'," +
            "  '%Secret Rare%'," +
            "  '%Ultra Rare%'" +
            "]) ORDER BY RANDOM() LIMIT ?",
            String.class, limit);
    }
}
