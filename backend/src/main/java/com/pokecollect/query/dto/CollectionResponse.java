package com.pokecollect.query.dto;

import java.util.List;

/** A user's full collection view with rollup totals. */
public record CollectionResponse(
    List<CollectionCardDto> cards,
    int totalCopies,
    int pricedCopies,
    double totalValue
) {}
