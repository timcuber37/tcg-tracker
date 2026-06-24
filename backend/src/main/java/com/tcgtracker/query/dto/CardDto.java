package com.tcgtracker.query.dto;

/** A single catalog card as returned to the SPA. */
public record CardDto(
    String pokewalletId,
    String cardName,
    String setName,
    String rarity,
    String cardType,
    Double marketPriceUsd
) {}
