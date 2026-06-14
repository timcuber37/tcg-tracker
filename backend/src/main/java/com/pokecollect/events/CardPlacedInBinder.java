package com.pokecollect.events;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Emitted when a card is placed into (or replaces) a binder pocket. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CardPlacedInBinder(
    String userId,
    String cardId,
    String cardName,
    String setName,
    String rarity,
    int pageNumber,
    int slotIndex,
    String eventType,
    String eventId,
    String timestamp
) {
    public static CardPlacedInBinder of(
        String userId, String cardId, String cardName, String setName,
        String rarity, int pageNumber, int slotIndex
    ) {
        return new CardPlacedInBinder(
            userId, cardId, cardName, setName, rarity, pageNumber, slotIndex,
            "card_placed_in_binder", UUID.randomUUID().toString(), Instant.now().toString());
    }
}
