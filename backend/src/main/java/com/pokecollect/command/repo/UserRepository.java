package com.pokecollect.command.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pokecollect.command.domain.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByUsername(String username);
    Optional<UserEntity> findByEmail(String email);
}
