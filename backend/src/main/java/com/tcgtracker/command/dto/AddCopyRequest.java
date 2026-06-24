package com.tcgtracker.command.dto;

public record AddCopyRequest(
    String pokewalletId,
    String condition
) {}
