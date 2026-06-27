package com.tcgtracker.consumer

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.SimpleStatement
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Kafka → Cassandra projector. Consumes collection events and maintains the
 * collection_by_user / binder_by_user read models. Active only under the `consumer`
 * profile, so a `web` instance never double-consumes. Ports consumers/cassandra_consumer.py.
 */
@Component
@Profile("consumer")
class CassandraProjector(
    private val cql: CqlSession,
    private val metrics: MeterRegistry,
) {

    @KafkaListener(topics = ["\${KAFKA_TOPIC:pokemon-tcg-events}"], groupId = "tcgtracker-cassandra-java")
    fun onEvent(json: String) {
        try {
            val e = MAPPER.readTree(json)
            val type = e.path("event_type").asText()
            when (type) {
                "card_added_to_collection" -> handleAdded(e)
                "card_removed_from_collection" -> handleRemoved(e)
                "card_placed_in_binder" -> handleBinderPlaced(e)
                "card_removed_from_binder" -> handleBinderRemoved(e)
                else -> return // ignore unrelated event types — don't count
            }
            metrics.counter("tcg.projections", "event_type", type).increment()
        } catch (ex: Exception) {
            log.error("Failed to process event: {}", json, ex)
        }
    }

    private fun handleAdded(e: JsonNode) {
        val priceNode = e.path("market_price_usd")
        val price = if (priceNode.isNull || priceNode.isMissingNode) null else BigDecimal(priceNode.asText())

        cql.execute(
            SimpleStatement.newInstance(
                INSERT,
                e.path("user_id").asText(),
                e.path("collection_id").asText(),
                e.path("card_id").asText(),
                e.path("card_name").asText(),
                e.path("set_name").asText(),
                e.path("rarity").asText(),
                e.path("condition").asText(),
                price,
            ),
        )
        log.info(
            "Cassandra: inserted card {} for user {}",
            e.path("card_name").asText(), e.path("user_id").asText(),
        )
    }

    private fun handleRemoved(e: JsonNode) {
        cql.execute(
            SimpleStatement.newInstance(
                DELETE,
                e.path("user_id").asText(),
                e.path("collection_id").asText(),
            ),
        )
        log.info(
            "Cassandra: removed collection {} for user {}",
            e.path("collection_id").asText(), e.path("user_id").asText(),
        )
    }

    private fun handleBinderPlaced(e: JsonNode) {
        cql.execute(
            SimpleStatement.newInstance(
                BINDER_INSERT,
                e.path("user_id").asText(),
                e.path("page_number").asInt(),
                e.path("slot_index").asInt(),
                e.path("card_id").asText(),
                e.path("card_name").asText(),
                e.path("set_name").asText(),
                e.path("rarity").asText(),
            ),
        )
        log.info(
            "Cassandra: placed {} in binder p{}s{} for user {}",
            e.path("card_name").asText(), e.path("page_number").asInt(),
            e.path("slot_index").asInt(), e.path("user_id").asText(),
        )
    }

    private fun handleBinderRemoved(e: JsonNode) {
        cql.execute(
            SimpleStatement.newInstance(
                BINDER_DELETE,
                e.path("user_id").asText(),
                e.path("page_number").asInt(),
                e.path("slot_index").asInt(),
            ),
        )
        log.info(
            "Cassandra: cleared binder p{}s{} for user {}",
            e.path("page_number").asInt(), e.path("slot_index").asInt(), e.path("user_id").asText(),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(CassandraProjector::class.java)
        private val MAPPER = ObjectMapper()

        private const val INSERT =
            "INSERT INTO collection_by_user " +
            "(user_id, collection_id, card_id, card_name, set_name, rarity, condition, market_price_usd, acquired_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, toTimestamp(now()))"

        private const val DELETE =
            "DELETE FROM collection_by_user WHERE user_id = ? AND collection_id = ?"

        private const val BINDER_INSERT =
            "INSERT INTO binder_by_user " +
            "(user_id, page_number, slot_index, card_id, card_name, set_name, rarity) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)"

        private const val BINDER_DELETE =
            "DELETE FROM binder_by_user WHERE user_id = ? AND page_number = ? AND slot_index = ?"
    }
}
