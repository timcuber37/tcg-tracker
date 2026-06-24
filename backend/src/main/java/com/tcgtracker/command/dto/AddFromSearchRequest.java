package com.tcgtracker.command.dto;

public record AddFromSearchRequest(
    String pokewalletId,
    String cardName,
    String setName,
    String rarity,
    String cardType,
    String condition,
    Double marketPriceUsd
) {}
