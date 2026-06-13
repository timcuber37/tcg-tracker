package com.pokecollect.query.cassandra;

import java.math.BigDecimal;

import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

/** Cassandra read model: cards grouped by set. PRIMARY KEY (set_name, card_id). */
@Table("cards_by_set")
public class CardsBySet {

    @PrimaryKeyColumn(name = "set_name", type = PrimaryKeyType.PARTITIONED)
    private String setName;

    @PrimaryKeyColumn(name = "card_id", type = PrimaryKeyType.CLUSTERED)
    private String cardId;

    @Column("card_name")
    private String cardName;

    private String rarity;

    @Column("card_type")
    private String cardType;

    @Column("market_price_usd")
    private BigDecimal marketPriceUsd;

    @Column("pokewallet_id")
    private String pokewalletId;

    public String getSetName()           { return setName; }
    public String getCardId()            { return cardId; }
    public String getCardName()          { return cardName; }
    public String getRarity()            { return rarity; }
    public String getCardType()          { return cardType; }
    public BigDecimal getMarketPriceUsd(){ return marketPriceUsd; }
    public String getPokewalletId()      { return pokewalletId; }
}
