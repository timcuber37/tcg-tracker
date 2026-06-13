package com.pokecollect.consumer;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Kafka → Cassandra projector. Consumes collection events and maintains the
 * collection_by_user read model. Active only under the `consumer` profile, so a
 * `web` instance never double-consumes. Ports consumers/cassandra_consumer.py.
 */
@Component
@Profile("consumer")
public class CassandraProjector {

    private static final Logger log = LoggerFactory.getLogger(CassandraProjector.class);

    private static final String INSERT =
        "INSERT INTO collection_by_user " +
        "(user_id, collection_id, card_id, card_name, set_name, rarity, condition, market_price_usd, acquired_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, toTimestamp(now()))";

    private static final String DELETE =
        "DELETE FROM collection_by_user WHERE user_id = ? AND collection_id = ?";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CqlSession cql;

    public CassandraProjector(CqlSession cql) {
        this.cql = cql;
    }

    @KafkaListener(topics = "${KAFKA_TOPIC:pokemon-tcg-events}", groupId = "pokecollect-cassandra-java")
    public void onEvent(String json) {
        try {
            JsonNode e = MAPPER.readTree(json);
            switch (e.path("event_type").asText()) {
                case "card_added_to_collection"     -> handleAdded(e);
                case "card_removed_from_collection" -> handleRemoved(e);
                default -> { /* ignore unrelated event types */ }
            }
        } catch (Exception ex) {
            log.error("Failed to process event: {}", json, ex);
        }
    }

    private void handleAdded(JsonNode e) {
        JsonNode priceNode = e.path("market_price_usd");
        BigDecimal price = priceNode.isNull() || priceNode.isMissingNode()
            ? null : new BigDecimal(priceNode.asText());

        cql.execute(SimpleStatement.newInstance(INSERT,
            e.path("user_id").asText(),
            e.path("collection_id").asText(),
            e.path("card_id").asText(),
            e.path("card_name").asText(),
            e.path("set_name").asText(),
            e.path("rarity").asText(),
            e.path("condition").asText(),
            price));
        log.info("Cassandra: inserted card {} for user {}",
            e.path("card_name").asText(), e.path("user_id").asText());
    }

    private void handleRemoved(JsonNode e) {
        cql.execute(SimpleStatement.newInstance(DELETE,
            e.path("user_id").asText(),
            e.path("collection_id").asText()));
        log.info("Cassandra: removed collection {} for user {}",
            e.path("collection_id").asText(), e.path("user_id").asText());
    }
}
