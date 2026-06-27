package com.tcgtracker.events

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.time.Instant
import java.util.UUID

/**
 * Emitted when a card is added to a collection. Serialized to snake_case JSON to
 * stay wire-compatible with the Python event schema (events/definitions.py), so
 * either app's consumer can read either app's events on the shared topic.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CardAddedToCollection(
    val userId: String,
    val cardId: String,
    val cardName: String,
    val setName: String,
    val rarity: String,
    val condition: String,
    val collectionId: String,
    val marketPriceUsd: Double?,
    val eventType: String,
    val eventId: String,
    val timestamp: String,
) {
    companion object {
        @JvmStatic
        fun of(
            userId: String, cardId: String, cardName: String, setName: String,
            rarity: String, condition: String, collectionId: String, marketPriceUsd: Double?,
        ): CardAddedToCollection = CardAddedToCollection(
            userId, cardId, cardName, setName, rarity, condition, collectionId, marketPriceUsd,
            "card_added_to_collection", UUID.randomUUID().toString(), Instant.now().toString(),
        )
    }
}
