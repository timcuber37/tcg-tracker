package com.tcgtracker.query.dto;

import java.util.List;

/** One deduplicated card in a collection: a card plus its copy count and the underlying rows. */
public record CollectionCardDto(
    String cardId,
    String cardName,
    String setName,
    String rarity,
    String condition,
    Double marketPriceUsd,
    int count,
    List<String> collectionIds
) {}
