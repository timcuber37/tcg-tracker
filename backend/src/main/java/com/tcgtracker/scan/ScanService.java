package com.tcgtracker.scan;

import org.springframework.stereotype.Service;

import com.tcgtracker.external.OcrResult;
import com.tcgtracker.external.VisionOcrClient;
import com.tcgtracker.scan.dto.ScanResponse;

/**
 * Card-scan pipeline: OCR the photo (Vision) → parse name + collector number →
 * rank catalog candidates. The SPA then lets the user confirm a candidate, which
 * flows into the existing add-to-collection command.
 */
@Service
public class ScanService {

    private static final int MAX_CANDIDATES = 5;

    private final VisionOcrClient vision;
    private final CardTextParser parser;
    private final CardMatcher matcher;

    public ScanService(VisionOcrClient vision, CardTextParser parser, CardMatcher matcher) {
        this.vision = vision;
        this.parser = parser;
        this.matcher = matcher;
    }

    public ScanResponse scan(byte[] imageBytes) {
        OcrResult ocr = vision.detect(imageBytes);
        ParsedCard parsed = parser.parse(ocr);
        return new ScanResponse(matcher.match(parsed, MAX_CANDIDATES), parsed);
    }
}
