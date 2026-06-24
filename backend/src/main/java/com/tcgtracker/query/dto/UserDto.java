package com.tcgtracker.query.dto;

/** The current authenticated user, as surfaced to the SPA. */
public record UserDto(
    String userId,
    String username,
    String email
) {}
