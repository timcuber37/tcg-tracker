package com.pokecollect.query.cassandra;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

/** Cassandra read model: a user's binder. PRIMARY KEY (user_id, page_number, slot_index). */
@Table("binder_by_user")
public class BinderByUser {

    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String userId;

    @PrimaryKeyColumn(name = "page_number", ordinal = 1, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private int pageNumber;

    @PrimaryKeyColumn(name = "slot_index", ordinal = 2, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private int slotIndex;

    @Column("card_id")
    private String cardId;

    @Column("card_name")
    private String cardName;

    @Column("set_name")
    private String setName;

    private String rarity;

    public String getUserId()    { return userId; }
    public int getPageNumber()   { return pageNumber; }
    public int getSlotIndex()    { return slotIndex; }
    public String getCardId()    { return cardId; }
    public String getCardName()  { return cardName; }
    public String getSetName()   { return setName; }
    public String getRarity()    { return rarity; }
}
