package com.tcgtracker.web;

import java.util.Map;

import org.springframework.security.oauth2.jwt.Jwt;

/** Helpers for pulling user fields out of a Supabase-issued JWT. */
final class JwtSupport {

    private JwtSupport() {}

    static String username(Jwt jwt) {
        Object meta = jwt.getClaim("user_metadata");
        if (meta instanceof Map<?, ?> m && m.get("username") instanceof String u && !u.isBlank()) {
            return u;
        }
        String email = jwt.getClaimAsString("email");
        return email != null && email.contains("@") ? email.substring(0, email.indexOf('@')) : jwt.getSubject();
    }

    static String email(Jwt jwt) {
        return jwt.getClaimAsString("email");
    }
}
