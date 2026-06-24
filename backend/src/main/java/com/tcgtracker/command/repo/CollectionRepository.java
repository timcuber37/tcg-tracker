package com.tcgtracker.command.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tcgtracker.command.domain.CollectionEntity;

public interface CollectionRepository extends JpaRepository<CollectionEntity, String> {
    List<CollectionEntity> findByUserId(String userId);
    List<CollectionEntity> findByUserIdAndCardId(String userId, String cardId);
    long countByUserIdAndCardId(String userId, String cardId);
}
