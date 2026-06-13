package com.pokecollect.external;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * PokéWallet REST client. Ports api/pokewallet.py.
 * 404 and 429 (rate limit) responses are swallowed and surfaced as null/empty
 * so callers degrade gracefully rather than throwing.
 */
@Component
public class PokeWalletClient {

    private static final Logger log = LoggerFactory.getLogger(PokeWalletClient.class);

    // Parse responses as String then readTree, so this works in every profile
    // regardless of which HTTP message converters the RestClient was built with.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient http;

    public PokeWalletClient(
        @Value("${pokewallet.base-url:https://api.pokewallet.io}") String baseUrl,
        @Value("${pokewallet.api-key:}") String apiKey
    ) {
        this.http = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("X-API-Key", apiKey)
            .build();
    }

    private JsonNode get(String path, Map<String, Object> query) {
        try {
            String body = http.get()
                .uri(b -> {
                    b.path(path);
                    query.forEach((k, v) -> b.queryParam(k, v));
                    return b.build();
                })
                .retrieve()
                .body(String.class);
            return body == null ? null : MAPPER.readTree(body);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429) {
                log.warn("PokéWallet rate limit hit for {}", path);
            } else if (status != 404) {
                log.error("PokéWallet API error for {}: {}", path, status);
            }
            return null;
        } catch (Exception e) {
            log.error("PokéWallet request failed for {}: {}", path, e.toString());
            return null;
        }
    }

    /** Full card detail + pricing by PokéWallet card ID. */
    public JsonNode getCard(String cardId) {
        return get("/cards/" + cardId, Map.of());
    }

    /** All sets (name, set_id, language, release_date, …). */
    public List<JsonNode> getAllSets() {
        JsonNode data = get("/sets", Map.of());
        return toList(data == null ? null : data.get("data"));
    }

    /** One page of cards for a set. Returns the raw node ({cards, pagination}) or null. */
    public JsonNode getSetCards(String setId, int page, int limit) {
        return get("/sets/" + setId, Map.of("page", page, "limit", limit));
    }

    /** Raw image bytes for a card. size = "low" | "high". Null on 404/429/error. */
    public byte[] getCardImageBytes(String cardId, String size) {
        try {
            return http.get()
                .uri(b -> b.path("/images/{id}").queryParam("size", size).build(cardId))
                .retrieve()
                .body(byte[].class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status != 404 && status != 429) {
                log.error("Image fetch error for {}: {}", cardId, status);
            }
            return null;
        } catch (Exception e) {
            log.error("Image fetch failed for {}: {}", cardId, e.toString());
            return null;
        }
    }

    /**
     * Lowest available TCGPlayer market price from a card response.
     * Prefers the "normal" variant, falls back to the first. Ports extract_tcgplayer_price.
     */
    public Double extractTcgPlayerPrice(JsonNode card) {
        if (card == null) {
            return null;
        }
        JsonNode tcg = card.path("tcgplayer");
        if (tcg.isMissingNode() || tcg.isNull()) {
            tcg = card.path("pricing").path("tcgplayer");
        }
        if (tcg.isMissingNode() || tcg.isNull()) {
            return null;
        }

        JsonNode pricesNode = tcg.path("prices");
        if (pricesNode.isMissingNode() || pricesNode.isNull()) {
            pricesNode = tcg.path("variants");
        }
        List<JsonNode> prices = toList(pricesNode);
        if (prices.isEmpty()) {
            return null;
        }

        JsonNode chosen = prices.stream()
            .filter(p -> "normal".equalsIgnoreCase(p.path("sub_type_name").asText("")))
            .findFirst()
            .orElse(prices.get(0));

        for (String key : List.of("market_price", "mid_price", "low_price")) {
            JsonNode v = chosen.path(key);
            if (!v.isMissingNode() && !v.isNull()) {
                return v.asDouble();
            }
        }
        return null;
    }

    /** Normalize an array node, or the values of an object node, into a List. */
    private static List<JsonNode> toList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isArray()) {
            return node.valueStream().toList();
        }
        if (node.isObject()) {
            return node.properties().stream().map(Map.Entry::getValue).toList();
        }
        return List.of();
    }
}
