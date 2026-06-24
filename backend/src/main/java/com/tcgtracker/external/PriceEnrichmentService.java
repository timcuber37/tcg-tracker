package com.tcgtracker.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Lazy live-price enrichment: on demand, fetch a card's current TCGPlayer price
 * from PokéWallet and cache it into the Postgres catalog. Ports
 * _fetch_and_cache_live_price from routes/command_routes.py. Resilient — returns
 * null on any failure so the caller can fall back.
 */
@Service
public class PriceEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(PriceEnrichmentService.class);

    private final PokeWalletClient pokewallet;
    private final JdbcTemplate pg;

    public PriceEnrichmentService(PokeWalletClient pokewallet,
                                  @Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate) {
        this.pokewallet = pokewallet;
        this.pg = postgresJdbcTemplate;
    }

    /** Fetch the live price for a card and upsert it into catalog_embeddings. Null if unavailable. */
    public Double fetchAndCache(String pokewalletId) {
        try {
            JsonNode card = pokewallet.getCard(pokewalletId);
            Double price = pokewallet.extractTcgPlayerPrice(card);
            if (price == null) {
                return null;
            }
            pg.update(
                "UPDATE catalog_embeddings SET market_price_usd = ?, updated_at = NOW() WHERE pokewallet_id = ?",
                price, pokewalletId);
            return price;
        } catch (Exception e) {
            log.warn("Live price fetch failed for {}: {}", pokewalletId, e.toString());
            return null;
        }
    }
}
