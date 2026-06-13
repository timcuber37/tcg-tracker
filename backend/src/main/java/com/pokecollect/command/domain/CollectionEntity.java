package com.pokecollect.command.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** MySQL {@code collections} table — one row per owned card copy. */
@Entity
@Table(name = "collections")
public class CollectionEntity {

    @Id
    @Column(name = "collection_id")
    private String collectionId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "card_id")
    private String cardId;

    // `condition` is a MySQL reserved word — quote the column name.
    @Column(name = "`condition`")
    private String condition;

    @Column(name = "acquired_at", insertable = false, updatable = false)
    private LocalDateTime acquiredAt;

    protected CollectionEntity() {}

    public CollectionEntity(String collectionId, String userId, String cardId, String condition) {
        this.collectionId = collectionId;
        this.userId = userId;
        this.cardId = cardId;
        this.condition = condition;
    }

    public String getCollectionId() { return collectionId; }
    public String getUserId()       { return userId; }
    public String getCardId()       { return cardId; }
    public String getCondition()    { return condition; }
    public LocalDateTime getAcquiredAt() { return acquiredAt; }
}
