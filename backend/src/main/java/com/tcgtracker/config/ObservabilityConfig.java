package com.tcgtracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Tags every metric with the application name and the role (web/consumer/sync) this
 * instance is playing, so the three profiles are distinguishable in InfluxDB/Grafana
 * even though they share one image and one measurement namespace.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name:tcg-tracker}") String application,
            @Value("${spring.profiles.active:web}") String role) {
        return registry -> registry.config().commonTags("application", application, "role", role);
    }
}
