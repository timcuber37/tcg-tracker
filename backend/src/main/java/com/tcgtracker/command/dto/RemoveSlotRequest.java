package com.tcgtracker.command.dto;

public record RemoveSlotRequest(
    int pageNumber,
    int slotIndex
) {}
