package com.tcgtracker.events

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks in the contract that matters for the Kotlin event slice: the data classes must
 * still serialize to **snake_case** JSON (via a plain ObjectMapper, like EventPublisher)
 * so the Kotlin events stay wire-compatible on the shared Kafka topic.
 */
class CardAddedToCollectionTest {

    private val mapper = ObjectMapper()

    @Test
    fun `of() stamps the event type plus a non-blank id and timestamp`() {
        val e = CardAddedToCollection.of("u1", "pw1", "Pikachu", "Base", "Rare", "Near Mint", "col1", 9.99)

        assertEquals("card_added_to_collection", e.eventType)
        assertTrue(e.eventId.isNotBlank())
        assertTrue(e.timestamp.isNotBlank())
    }

    @Test
    fun `serializes to snake_case JSON, not camelCase`() {
        val e = CardAddedToCollection.of("u1", "pw1", "Pikachu", "Base", "Rare", "Near Mint", "col1", 9.99)

        val json = mapper.readTree(mapper.writeValueAsString(e))

        assertEquals("u1", json.get("user_id").asText())
        assertEquals("pw1", json.get("card_id").asText())
        assertEquals("Near Mint", json.get("condition").asText())
        assertEquals("card_added_to_collection", json.get("event_type").asText())
        assertEquals(9.99, json.get("market_price_usd").asDouble())
        // The camelCase property names must not leak onto the wire.
        assertNull(json.get("userId"))
        assertNull(json.get("marketPriceUsd"))
    }

    @Test
    fun `a null market price serializes as JSON null`() {
        val e = CardAddedToCollection.of("u1", "pw1", "Pikachu", "Base", "Rare", "Near Mint", "col1", null)

        val json = mapper.readTree(mapper.writeValueAsString(e))

        assertTrue(json.get("market_price_usd").isNull)
    }
}
