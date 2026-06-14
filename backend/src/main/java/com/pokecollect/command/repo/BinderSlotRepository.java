package com.pokecollect.command.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pokecollect.command.domain.BinderSlotEntity;

public interface BinderSlotRepository extends JpaRepository<BinderSlotEntity, String> {
    Optional<BinderSlotEntity> findByUserIdAndPageNumberAndSlotIndex(String userId, int pageNumber, int slotIndex);
    List<BinderSlotEntity> findByUserId(String userId);
    long countByUserIdAndCardId(String userId, String cardId);
}
