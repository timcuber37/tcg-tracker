package com.pokecollect.command.dto;

public record PlaceCardRequest(
    String cardId,
    int pageNumber,
    int slotIndex
) {}
