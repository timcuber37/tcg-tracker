package com.pokecollect.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** MySQL {@code cards} master catalog. card_id reuses the PokéWallet ID. */
@Entity
@Table(name = "cards")
public class CardEntity {

    @Id
    @Column(name = "card_id")
    private String cardId;

    private String name;

    @Column(name = "set_name")
    private String setName;

    private String rarity;

    @Column(name = "card_type")
    private String cardType;

    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "pokewallet_id")
    private String pokewalletId;

    protected CardEntity() {}

    public CardEntity(String cardId, String name, String setName, String rarity,
                      String cardType, String pokewalletId) {
        this.cardId = cardId;
        this.name = name;
        this.setName = setName;
        this.rarity = rarity;
        this.cardType = cardType;
        this.pokewalletId = pokewalletId;
    }

    public String getCardId()       { return cardId; }
    public String getName()         { return name; }
    public String getSetName()      { return setName; }
    public String getRarity()       { return rarity; }
    public String getCardType()     { return cardType; }
    public String getDescription()  { return description; }
    public String getImageUrl()     { return imageUrl; }
    public String getPokewalletId() { return pokewalletId; }
}
