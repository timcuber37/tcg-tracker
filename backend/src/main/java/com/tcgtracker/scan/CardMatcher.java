package com.tcgtracker.scan;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tcgtracker.query.CatalogSearchService;
import com.tcgtracker.query.dto.CardDto;
import com.tcgtracker.scan.dto.ScanCandidate;

/**
 * Ranks catalog listings against a {@link ParsedCard}. The collector number is the
 * strong signal (it lives inside the catalog's card_name); the Pokémon name breaks
 * ties across sets and rescues scans where the number wasn't read.
 */
@Component
public class CardMatcher {

    private static final int POOL_LIMIT = 80;

    // Score weights: an exact collector-number hit dominates; name similarity refines.
    private static final double NUMBER_WEIGHT = 0.6;
    private static final double NAME_WEIGHT = 0.4;

    private final CatalogSearchService catalog;

    public CardMatcher(CatalogSearchService catalog) {
        this.catalog = catalog;
    }

    public List<ScanCandidate> match(ParsedCard parsed, int topK) {
        if (parsed == null) {
            return List.of();
        }
        // Gather a candidate pool by number and/or name, de-duplicated by id.
        Map<String, CardDto> pool = new LinkedHashMap<>();
        if (parsed.collectorNumber() != null) {
            for (CardDto c : catalog.findByCardNameContains(parsed.collectorNumber(), POOL_LIMIT)) {
                pool.putIfAbsent(c.pokewalletId(), c);
            }
        }
        String token = primaryToken(parsed.name());
        if (token != null) {
            for (CardDto c : catalog.findByCardNameContains(token, POOL_LIMIT)) {
                pool.putIfAbsent(c.pokewalletId(), c);
            }
        }

        return pool.values().stream()
            .map(c -> new ScanCandidate(c, score(parsed, c)))
            .filter(sc -> sc.confidence() > 0)
            .sorted(Comparator.comparingDouble(ScanCandidate::confidence).reversed())
            .limit(topK)
            .toList();
    }

    /** 0..1 confidence: collector-number exactness + name similarity. */
    static double score(ParsedCard parsed, CardDto card) {
        double score = 0;
        String cardName = card.cardName() == null ? "" : card.cardName().toLowerCase();

        if (parsed.collectorNumber() != null
                && cardName.contains(parsed.collectorNumber().toLowerCase())) {
            score += NUMBER_WEIGHT;
        }
        if (parsed.name() != null && !parsed.name().isBlank()) {
            score += NAME_WEIGHT * nameSimilarity(parsed.name(), baseName(card.cardName()));
        }
        return Math.min(1.0, score);
    }

    /** Catalog names embed the number ("Xerneas - 091/086"); strip it for name comparison. */
    static String baseName(String cardName) {
        if (cardName == null) {
            return "";
        }
        return cardName.replaceAll("\\s*-\\s*\\S+\\s*$", "").trim();
    }

    /** The most distinctive token of a parsed name (longest word) used to widen the DB query. */
    static String primaryToken(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String best = null;
        for (String w : name.trim().split("\\s+")) {
            String t = w.replaceAll("[^A-Za-z]", "");
            if (t.length() >= 3 && (best == null || t.length() > best.length())) {
                best = t;
            }
        }
        return best;
    }

    /** Normalized Levenshtein similarity in [0,1], case-insensitive. */
    static double nameSimilarity(String a, String b) {
        String x = a == null ? "" : a.toLowerCase().trim();
        String y = b == null ? "" : b.toLowerCase().trim();
        if (x.isEmpty() && y.isEmpty()) {
            return 1.0;
        }
        int max = Math.max(x.length(), y.length());
        if (max == 0) {
            return 1.0;
        }
        return 1.0 - (double) levenshtein(x, y) / max;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }
}
