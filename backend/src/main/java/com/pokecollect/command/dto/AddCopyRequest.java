package com.pokecollect.command.dto;

public record AddCopyRequest(
    String pokewalletId,
    String condition
) {}
