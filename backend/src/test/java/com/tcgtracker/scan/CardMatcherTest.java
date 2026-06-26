package com.tcgtracker.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tcgtracker.query.dto.CardDto;

class CardMatcherTest {

    private static CardDto card(String name) {
        return new CardDto("pk_" + name.hashCode(), name, "Set", "Rare", "Psychic", 1.0);
    }

    @Test
    void baseNameStripsTrailingCollectorNumber() {
        assertEquals("Xerneas", CardMatcher.baseName("Xerneas - 091/086"));
        assertEquals("Pikachu", CardMatcher.baseName("Pikachu"));
    }

    @Test
    void primaryTokenPicksLongestWord() {
        assertEquals("Valiant", CardMatcher.primaryToken("Iron Valiant ex"));
        assertEquals("Pikachu", CardMatcher.primaryToken("Pikachu"));
    }

    @Test
    void nameSimilarityIsHighForCloseStrings() {
        assertEquals(1.0, CardMatcher.nameSimilarity("Xerneas", "Xerneas"), 1e-9);
        assertTrue(CardMatcher.nameSimilarity("Xerneas", "Xernaes") > 0.7);
        assertTrue(CardMatcher.nameSimilarity("Xerneas", "Charizard") < 0.4);
    }

    @Test
    void exactNumberAndNameScoresFull() {
        ParsedCard parsed = new ParsedCard("Xerneas", "091/086", null);
        assertEquals(1.0, CardMatcher.score(parsed, card("Xerneas - 091/086")), 1e-9);
    }

    @Test
    void wrongNumberSameNameScoresLowerThanExact() {
        ParsedCard parsed = new ParsedCard("Xerneas", "091/086", null);
        double exact = CardMatcher.score(parsed, card("Xerneas - 091/086"));
        double wrongNumber = CardMatcher.score(parsed, card("Xerneas - 042/086"));
        assertTrue(exact > wrongNumber, "exact-number match should outscore a different number");
        assertTrue(wrongNumber > 0, "same-name card should still score on name similarity");
    }
}
