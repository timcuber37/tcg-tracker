package com.tcgtracker.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tcgtracker.query.CollectionPriceRefreshService;
import com.tcgtracker.query.CollectionService;
import com.tcgtracker.query.dto.CollectionResponse;
import com.tcgtracker.query.dto.RefreshStatus;

/** Authenticated collection view for the current user (JWT sub = user_id). */
@RestController
@RequestMapping("/api/collection")
public class CollectionController {

    private final CollectionService collection;
    private final CollectionPriceRefreshService priceRefresh;

    public CollectionController(CollectionService collection, CollectionPriceRefreshService priceRefresh) {
        this.collection = collection;
        this.priceRefresh = priceRefresh;
    }

    @GetMapping
    public CollectionResponse myCollection(@AuthenticationPrincipal Jwt jwt) {
        return collection.forUser(jwt.getSubject());
    }

    /**
     * Kick off a background refresh of live prices for every set the user owns
     * cards in. Returns immediately — the run takes minutes, so the client polls
     * {@link #refreshStatus} for completion. 202 when started, 200 if one is
     * already running, 429 (with Retry-After) when throttled by the cooldown.
     */
    @PostMapping("/refresh-prices")
    public ResponseEntity<RefreshStatus> startRefresh(@AuthenticationPrincipal Jwt jwt) {
        RefreshStatus status = priceRefresh.start(jwt.getSubject());
        return switch (status.state()) {
            case "started" -> ResponseEntity.accepted().body(status);
            case "cooling" -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(status.cooldownRemainingSeconds()))
                .body(status);
            default -> ResponseEntity.ok(status); // "running"
        };
    }

    /** Poll the current refresh status for the user (state + last run's counts). */
    @GetMapping("/refresh-prices/status")
    public RefreshStatus refreshStatus(@AuthenticationPrincipal Jwt jwt) {
        return priceRefresh.status(jwt.getSubject());
    }
}
