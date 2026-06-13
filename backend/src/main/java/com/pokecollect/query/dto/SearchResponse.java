package com.pokecollect.query.dto;

import java.util.List;

/** Paginated catalog search result. */
public record SearchResponse(
    List<CardDto> results,
    long total,
    int page,
    int totalPages,
    int pageSize
) {}
