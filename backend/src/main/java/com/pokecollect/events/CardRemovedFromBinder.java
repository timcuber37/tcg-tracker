package com.pokecollect.events;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** Emitted when a binder pocket is cleared. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CardRemovedFromBinder(
    String userId,
    int pageNumber,
    int slotIndex,
    String eventType,
    String eventId,
    String timestamp
) {
    public static CardRemovedFromBinder of(String userId, int pageNumber, int slotIndex) {
        return new CardRemovedFromBinder(
            userId, pageNumber, slotIndex,
            "card_removed_from_binder", UUID.randomUUID().toString(), Instant.now().toString());
    }
}
