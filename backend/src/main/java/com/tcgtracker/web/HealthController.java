package com.tcgtracker.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 0 smoke endpoint — confirms the app boots and serves JSON.
 * Replaced/expanded by real query endpoints in Phase 1.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${spring.profiles.active:unknown}")
    private String activeProfile;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "ok",
            "service", "tcg-tracker",
            "profile", activeProfile,
            "timestamp", Instant.now().toString()
        );
    }
}
