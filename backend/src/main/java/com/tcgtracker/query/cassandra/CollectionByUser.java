package com.tcgtracker.query.cassandra;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

/** Cassandra read model: a user's collection. PRIMARY KEY (user_id, collection_id). */
@Table("collection_by_user")
public class CollectionByUser {

    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED)
    private String userId;

    @PrimaryKeyColumn(name = "collection_id", type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private String collectionId;

    @Column("card_id")
    private String cardId;

    @Column("card_name")
    private String cardName;

    @Column("set_name")
    private String setName;

    private String rarity;
    private String condition;

    @Column("market_price_usd")
    private BigDecimal marketPriceUsd;

    @Column("acquired_at")
    private Instant acquiredAt;

    public String getUserId()            { return userId; }
    public String getCollectionId()      { return collectionId; }
    public String getCardId()            { return cardId; }
    public String getCardName()          { return cardName; }
    public String getSetName()           { return setName; }
    public String getRarity()            { return rarity; }
    public String getCondition()         { return condition; }
    public BigDecimal getMarketPriceUsd(){ return marketPriceUsd; }
    public Instant getAcquiredAt()       { return acquiredAt; }
}
