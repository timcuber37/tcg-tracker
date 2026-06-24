package com.tcgtracker.events;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Serializes domain events to JSON and publishes them to the shared Kafka topic. */
@Component
public class EventPublisher {

    // Self-contained mapper: this runs in headless profiles where the web
    // ObjectMapper bean isn't present. @JsonNaming on the events is honored regardless.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public EventPublisher(
        KafkaTemplate<String, String> kafka,
        @Value("${KAFKA_TOPIC:pokemon-tcg-events}") String topic
    ) {
        this.kafka = kafka;
        this.topic = topic;
    }

    public void publish(Object event) {
        try {
            kafka.send(topic, MAPPER.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}
