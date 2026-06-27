package com.tcgtracker.events

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.Instant
import java.util.UUID

/** Emitted when a card is placed into (or replaces) a binder pocket. Snake_case JSON. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CardPlacedInBinder(
    val userId: String,
    val cardId: String,
    val cardName: String,
    val setName: String,
    val rarity: String,
    val pageNumber: Int,
    val slotIndex: Int,
    val eventType: String,
    val eventId: String,
    val timestamp: String,
) {
    companion object {
        @JvmStatic
        fun of(
            userId: String, cardId: String, cardName: String, setName: String,
            rarity: String, pageNumber: Int, slotIndex: Int,
        ): CardPlacedInBinder = CardPlacedInBinder(
            userId, cardId, cardName, setName, rarity, pageNumber, slotIndex,
            "card_placed_in_binder", UUID.randomUUID().toString(), Instant.now().toString(),
        )
    }
}
