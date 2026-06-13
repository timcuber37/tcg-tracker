package com.pokecollect.events;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Emitted when a card is added to a collection. Serialized to snake_case JSON to
 * stay wire-compatible with the Python event schema (events/definitions.py), so
 * either app's consumer can read either app's events on the shared topic.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CardAddedToCollection(
    String userId,
    String cardId,
    String cardName,
    String setName,
    String rarity,
    String condition,
    String collectionId,
    Double marketPriceUsd,
    String eventType,
    String eventId,
    String timestamp
) {
    public static CardAddedToCollection of(
        String userId, String cardId, String cardName, String setName,
        String rarity, String condition, String collectionId, Double marketPriceUsd
    ) {
        return new CardAddedToCollection(
            userId, cardId, cardName, setName, rarity, condition, collectionId, marketPriceUsd,
            "card_added_to_collection", UUID.randomUUID().toString(), Instant.now().toString());
    }
}
