package com.tcgtracker.events

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.Instant
import java.util.UUID

/** Emitted when a binder pocket is cleared. Snake_case JSON (see [CardAddedToCollection]). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CardRemovedFromBinder(
    val userId: String,
    val pageNumber: Int,
    val slotIndex: Int,
    val eventType: String,
    val eventId: String,
    val timestamp: String,
) {
    companion object {
        @JvmStatic
        fun of(userId: String, pageNumber: Int, slotIndex: Int): CardRemovedFromBinder =
            CardRemovedFromBinder(
                userId, pageNumber, slotIndex,
                "card_removed_from_binder", UUID.randomUUID().toString(), Instant.now().toString(),
            )
    }
}
