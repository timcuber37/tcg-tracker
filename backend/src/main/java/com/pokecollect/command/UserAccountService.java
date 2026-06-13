package com.pokecollect.command;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.pokecollect.command.domain.UserEntity;
import com.pokecollect.command.repo.UserRepository;
import com.pokecollect.query.dto.UserDto;

/**
 * Mirrors a Supabase Auth user into the MySQL {@code users} table so the rest of
 * the app (collections, Cassandra read models) can reference a local user_id that
 * equals the Supabase UUID. Credentials live in Supabase — the shadow row stores
 * only username/email for display and foreign-key linkage.
 */
@Service
public class UserAccountService {

    private final UserRepository users;

    public UserAccountService(UserRepository users) {
        this.users = users;
    }

    /** Idempotently ensure a shadow row exists for the authenticated user. */
    public UserDto sync(String userId, String username, String email) {
        UserEntity existing = users.findById(userId).orElse(null);
        if (existing != null) {
            return toDto(existing);
        }
        try {
            UserEntity saved = users.save(new UserEntity(userId, username, email, ""));
            return toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            // username/email already taken by a different account — fall back to a unique handle.
            UserEntity saved = users.save(new UserEntity(userId, username + "_" + userId.substring(0, 8), email, ""));
            return toDto(saved);
        }
    }

    public UserDto current(String userId, String fallbackUsername, String fallbackEmail) {
        return users.findById(userId)
            .map(this::toDto)
            .orElse(new UserDto(userId, fallbackUsername, fallbackEmail));
    }

    private UserDto toDto(UserEntity u) {
        return new UserDto(u.getUserId(), u.getUsername(), u.getEmail());
    }
}
