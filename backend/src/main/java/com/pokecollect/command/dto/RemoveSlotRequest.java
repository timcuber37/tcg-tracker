package com.pokecollect.command.dto;

public record RemoveSlotRequest(
    int pageNumber,
    int slotIndex
) {}
