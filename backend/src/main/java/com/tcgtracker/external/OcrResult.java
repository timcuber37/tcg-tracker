package com.tcgtracker.external;

import java.util.List;

/**
 * Normalized OCR output from {@link VisionOcrClient}: the full detected text plus
 * the individual words with bounding boxes. Decouples the rest of the scan
 * pipeline from Google Vision's raw JSON shape.
 */
public record OcrResult(String fullText, List<OcrWord> words) {

    public static OcrResult empty() {
        return new OcrResult("", List.of());
    }
}
