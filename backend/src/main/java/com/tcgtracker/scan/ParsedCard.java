package com.tcgtracker.scan;

/**
 * What {@link CardTextParser} extracted from a scanned card's OCR. Any field may
 * be null when the card lacks it or OCR couldn't read it (older cards have no
 * collector number; set codes are often unreadable).
 */
public record ParsedCard(String name, String collectorNumber, String setCode) {

    public static ParsedCard empty() {
        return new ParsedCard(null, null, null);
    }
}
