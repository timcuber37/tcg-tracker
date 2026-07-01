package com.tcgtracker.query;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tcgtracker.query.cassandra.CollectionByUser;
import com.tcgtracker.query.cassandra.CollectionByUserRepository;
import com.tcgtracker.query.dto.RefreshStatus;
import com.tcgtracker.sync.CatalogRefreshService;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;

/**
 * On-demand "refresh the prices of the cards in my collection". Resolves the
 * distinct sets the user owns cards in, then refreshes each whole set from
 * PokéWallet via {@link CatalogRefreshService} — one API call per set page (plus
 * one {@code /sets} call to map names to ids), far cheaper than one call per card.
 *
 * Runs <b>asynchronously</b>: a collection spanning many sets takes minutes of
 * sequential PokéWallet calls, well past any HTTP/proxy timeout, so the request
 * only kicks the job off and the client polls {@link #status} for completion.
 * Refreshed prices land in the catalog and surface the next time the collection
 * view overlays current prices.
 *
 * A per-user cooldown keeps this within the PokéWallet free tier; the daily
 * {@link com.tcgtracker.sync.CatalogSyncJob} already refreshes everything in the
 * background, so this only buys intra-day freshness on demand.
 */
@Service
public class CollectionPriceRefreshService {

    private static final Logger log = LoggerFactory.getLogger(CollectionPriceRefreshService.class);

    private final CollectionByUserRepository collectionRepo;
    private final CatalogRefreshService catalogRefresh;
    private final Timer refreshTimer;
    private final long cooldownSeconds;

    // Single worker: refreshes run one at a time across all users, keeping the
    // PokéWallet call rate predictable and within the free tier.
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "price-refresh");
        t.setDaemon(true);
        return t;
    });

    // Per-user state. In-memory: a single web instance backs the app, and the
    // cooldown is a courtesy throttle, not a correctness guarantee.
    private final Map<String, Boolean> running = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastRefresh = new ConcurrentHashMap<>();
    private final Map<String, RefreshStatus> lastResult = new ConcurrentHashMap<>();

    public CollectionPriceRefreshService(CollectionByUserRepository collectionRepo,
                                         CatalogRefreshService catalogRefresh,
                                         MeterRegistry metrics,
                                         @Value("${collection.refresh.cooldown-seconds:3600}") long cooldownSeconds) {
        this.collectionRepo = collectionRepo;
        this.catalogRefresh = catalogRefresh;
        this.cooldownSeconds = cooldownSeconds;
        // End-to-end latency of a refresh run (sets map + per-set fetches + upserts),
        // with percentiles so Grafana can chart p95/p99.
        this.refreshTimer = Timer.builder("collection.price.refresh")
            .description("On-demand collection price refresh latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(metrics);
    }

    /** Kick off a background refresh if allowed; returns the resulting status snapshot. */
    public RefreshStatus start(String userId) {
        long remaining = cooldownRemaining(userId);
        if (remaining > 0) {
            return snapshot("cooling", remaining, userId);
        }
        // putIfAbsent acts as a per-user lock: only the first caller starts a run.
        if (running.putIfAbsent(userId, Boolean.TRUE) != null) {
            return snapshot("running", 0, userId);
        }
        worker.submit(() -> runRefresh(userId));
        return snapshot("started", 0, userId);
    }

    /** Current status for a user, with no side effects. */
    public RefreshStatus status(String userId) {
        long remaining = cooldownRemaining(userId);
        String state = running.containsKey(userId) ? "running"
                     : remaining > 0 ? "cooling" : "idle";
        return snapshot(state, remaining, userId);
    }

    private void runRefresh(String userId) {
        try {
            refreshTimer.record(() -> doRefresh(userId));
            lastRefresh.put(userId, Instant.now());
        } catch (Exception e) {
            log.error("Collection refresh failed for {}: {}", userId, e.toString(), e);
        } finally {
            running.remove(userId);
        }
    }

    private void doRefresh(String userId) {
        // Distinct set names the user owns cards in.
        List<String> ownedSets = collectionRepo.findByUserId(userId).stream()
            .map(CollectionByUser::getSetName)
            .filter(s -> s != null && !s.isBlank())
            .distinct()
            .toList();

        if (ownedSets.isEmpty()) {
            lastResult.put(userId, new RefreshStatus("idle", 0, 0, 0, 0, 0));
            return;
        }

        Map<String, JsonNode> setInfoByName = catalogRefresh.englishSetInfoByName();
        int apiCalls = 1; // the /sets call above
        int setsRefreshed = 0;
        int setsUnmatched = 0;
        int cardsUpdated = 0;

        for (String setName : ownedSets) {
            JsonNode info = setInfoByName.get(setName);
            if (info == null) {
                setsUnmatched++;
                log.warn("Collection refresh: no PokéWallet set matched name '{}' for user {}", setName, userId);
                continue;
            }
            CatalogRefreshService.SetRefresh r = catalogRefresh.refreshSet(info);
            apiCalls += r.pagesFetched();
            cardsUpdated += r.cardsUpdated();
            setsRefreshed++;
        }

        log.info("Collection refresh for {}: {} set(s), {} card(s) updated, {} API call(s), {} unmatched",
            userId, setsRefreshed, cardsUpdated, apiCalls, setsUnmatched);
        lastResult.put(userId, new RefreshStatus("idle", 0, setsRefreshed, setsUnmatched, cardsUpdated, apiCalls));
    }

    /** Build a status with the current state plus the last completed run's counts (if any). */
    private RefreshStatus snapshot(String state, long cooldownRemaining, String userId) {
        RefreshStatus last = lastResult.get(userId);
        return new RefreshStatus(state, cooldownRemaining,
            last == null ? null : last.setsRefreshed(),
            last == null ? null : last.setsUnmatched(),
            last == null ? null : last.cardsUpdated(),
            last == null ? null : last.apiCalls());
    }

    private long cooldownRemaining(String userId) {
        Instant last = lastRefresh.get(userId);
        if (last == null) {
            return 0;
        }
        long elapsed = Duration.between(last, Instant.now()).getSeconds();
        return Math.max(0, cooldownSeconds - elapsed);
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
