package com.tcgtracker.command.dto;

public record PlaceCardRequest(
    String cardId,
    int pageNumber,
    int slotIndex
) {}
