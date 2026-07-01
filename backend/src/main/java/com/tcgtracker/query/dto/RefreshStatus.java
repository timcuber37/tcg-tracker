package com.tcgtracker.query.dto;

/**
 * State of a user's background price refresh.
 *
 * {@code state} is one of:
 *  - "started" — a refresh was just kicked off by this request,
 *  - "running" — a refresh is already in progress,
 *  - "cooling" — throttled by the cooldown (see {@code cooldownRemainingSeconds}),
 *  - "idle"    — nothing running and not cooling.
 *
 * The count fields summarize the most recent completed run, or are null if none
 * has finished since startup.
 */
public record RefreshStatus(
    String state,
    long cooldownRemainingSeconds,
    Integer setsRefreshed,
    Integer setsUnmatched,
    Integer cardsUpdated,
    Integer apiCalls
) {}
