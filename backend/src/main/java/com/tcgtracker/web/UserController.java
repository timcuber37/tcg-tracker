package com.tcgtracker.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tcgtracker.command.UserAccountService;
import com.tcgtracker.query.dto.UserDto;

/**
 * Authenticated user endpoints. The SPA calls /sync once after Supabase sign-up
 * to create the MySQL shadow row; /me returns the current account.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserAccountService accounts;

    public UserController(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/sync")
    public UserDto sync(@AuthenticationPrincipal Jwt jwt) {
        return accounts.sync(jwt.getSubject(), JwtSupport.username(jwt), JwtSupport.email(jwt));
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return accounts.current(jwt.getSubject(), JwtSupport.username(jwt), JwtSupport.email(jwt));
    }
}
