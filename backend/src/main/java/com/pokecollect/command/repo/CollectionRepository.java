package com.pokecollect.command.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pokecollect.command.domain.CollectionEntity;

public interface CollectionRepository extends JpaRepository<CollectionEntity, String> {
    List<CollectionEntity> findByUserId(String userId);
    List<CollectionEntity> findByUserIdAndCardId(String userId, String cardId);
}
