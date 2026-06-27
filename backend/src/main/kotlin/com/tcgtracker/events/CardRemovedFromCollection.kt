package com.tcgtracker.events

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.Instant
import java.util.UUID

/** Emitted when a card copy is removed from a collection. Snake_case JSON (see [CardAddedToCollection]). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CardRemovedFromCollection(
    val userId: String,
    val cardId: String,
    val cardName: String,
    val collectionId: String,
    val eventType: String,
    val eventId: String,
    val timestamp: String,
) {
    companion object {
        @JvmStatic
        fun of(userId: String, cardId: String, cardName: String, collectionId: String): CardRemovedFromCollection =
            CardRemovedFromCollection(
                userId, cardId, cardName, collectionId,
                "card_removed_from_collection", UUID.randomUUID().toString(), Instant.now().toString(),
            )
    }
}
