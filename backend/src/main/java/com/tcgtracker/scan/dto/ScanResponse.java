package com.tcgtracker.scan.dto;

import java.util.List;

import com.tcgtracker.scan.ParsedCard;

/**
 * Result of POST /api/scan: the ranked candidate listings (best first) plus what
 * OCR parsed, so the SPA can show the read text and let the user confirm a match.
 */
public record ScanResponse(List<ScanCandidate> candidates, ParsedCard parsed) {}
