package com.pokecollect.events;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Emitted when a card copy is removed from a collection. Snake_case JSON (see CardAddedToCollection). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CardRemovedFromCollection(
    String userId,
    String cardId,
    String cardName,
    String collectionId,
    String eventType,
    String eventId,
    String timestamp
) {
    public static CardRemovedFromCollection of(
        String userId, String cardId, String cardName, String collectionId
    ) {
        return new CardRemovedFromCollection(
            userId, cardId, cardName, collectionId,
            "card_removed_from_collection", UUID.randomUUID().toString(), Instant.now().toString());
    }
}
