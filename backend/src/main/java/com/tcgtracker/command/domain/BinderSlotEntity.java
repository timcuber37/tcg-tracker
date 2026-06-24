package com.tcgtracker.command.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** MySQL {@code binder_slots} — one row per filled binder pocket. */
@Entity
@Table(name = "binder_slots")
public class BinderSlotEntity {

    @Id
    @Column(name = "slot_id")
    private String slotId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "page_number")
    private int pageNumber;

    @Column(name = "slot_index")
    private int slotIndex;

    @Column(name = "card_id")
    private String cardId;

    @Column(name = "placed_at", insertable = false, updatable = false)
    private LocalDateTime placedAt;

    protected BinderSlotEntity() {}

    public BinderSlotEntity(String slotId, String userId, int pageNumber, int slotIndex, String cardId) {
        this.slotId = slotId;
        this.userId = userId;
        this.pageNumber = pageNumber;
        this.slotIndex = slotIndex;
        this.cardId = cardId;
    }

    public String getSlotId()       { return slotId; }
    public String getUserId()       { return userId; }
    public int getPageNumber()      { return pageNumber; }
    public int getSlotIndex()       { return slotIndex; }
    public String getCardId()       { return cardId; }
    public LocalDateTime getPlacedAt() { return placedAt; }
}
