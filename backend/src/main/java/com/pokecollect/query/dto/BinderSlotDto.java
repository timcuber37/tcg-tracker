package com.pokecollect.query.dto;

/** One filled binder pocket. */
public record BinderSlotDto(
    int pageNumber,
    int slotIndex,
    String cardId,
    String cardName,
    String setName,
    String rarity
) {}
