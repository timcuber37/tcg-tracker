package com.tcgtracker.scan.dto;

import com.tcgtracker.query.dto.CardDto;

/** A catalog card matched to a scan, with a 0..1 confidence the user can sanity-check. */
public record ScanCandidate(CardDto card, double confidence) {}
