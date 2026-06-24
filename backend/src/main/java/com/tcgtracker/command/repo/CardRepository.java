package com.tcgtracker.command.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tcgtracker.command.domain.CardEntity;

public interface CardRepository extends JpaRepository<CardEntity, String> {
    Optional<CardEntity> findByPokewalletId(String pokewalletId);
}
