package com.tcgtracker.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tcgtracker.command.domain.CardEntity;
import com.tcgtracker.command.domain.CollectionEntity;
import com.tcgtracker.command.repo.CardRepository;
import com.tcgtracker.command.repo.CollectionRepository;
import com.tcgtracker.events.CardAddedToCollection;
import com.tcgtracker.events.CardRemovedFromCollection;
import com.tcgtracker.events.EventPublisher;
import com.tcgtracker.external.PriceEnrichmentService;
import com.tcgtracker.query.CatalogSearchService;

/**
 * Write side: persists to the MySQL source of truth and publishes a Kafka event
 * so the read models can project it. Ports commands/handlers.py.
 *
 * Live PokéWallet price enrichment on add (Python's _fetch_and_cache_live_price)
 * is deferred to Phase 4; for now the price passed from the search result is used.
 */
@Service
public class CommandHandler {

    private static final String DEFAULT_CONDITION = "Near Mint";

    private final CardRepository cards;
    private final CollectionRepository collections;
    private final EventPublisher events;
    private final CatalogSearchService catalog;
    private final PriceEnrichmentService priceEnrichment;

    public CommandHandler(CardRepository cards, CollectionRepository collections,
                          EventPublisher events, CatalogSearchService catalog,
                          PriceEnrichmentService priceEnrichment) {
        this.cards = cards;
        this.collections = collections;
        this.events = events;
        this.catalog = catalog;
        this.priceEnrichment = priceEnrichment;
    }

    /** Add a card from a search result. card_id reuses the PokéWallet ID (created lazily). */
    @Transactional
    public String addFromSearch(String userId, String pokewalletId, String cardName, String setName,
                                String rarity, String cardType, String condition, Double marketPriceUsd) {
        // Lazily fetch + cache a live price when the caller didn't supply one.
        if (marketPriceUsd == null) {
            marketPriceUsd = priceEnrichment.fetchAndCache(pokewalletId);
        }

        cards.findById(pokewalletId).orElseGet(() ->
            cards.save(new CardEntity(pokewalletId, cardName, setName, rarity, cardType, pokewalletId)));

        String collectionId = UUID.randomUUID().toString();
        collections.save(new CollectionEntity(collectionId, userId, pokewalletId, normalize(condition)));

        events.publish(CardAddedToCollection.of(
            userId, pokewalletId, cardName, setName, rarity, normalize(condition), collectionId, marketPriceUsd));
        return collectionId;
    }

    /** Add another copy of a card already in the catalog. */
    public String addCopy(String userId, String pokewalletId, String condition) {
        CatalogSearchService.CatalogCard card = catalog.cardByPokewalletId(pokewalletId);
        if (card == null) {
            return null;
        }
        return addFromSearch(
            userId, pokewalletId, card.cardName(), card.setName(),
            card.rarity() != null ? card.rarity() : "Unknown",
            card.cardType() != null ? card.cardType() : "Unknown",
            condition, card.marketPriceUsd());
    }

    /** Remove one copy. No-op if the row is missing or not owned by the requesting user. */
    @Transactional
    public boolean removeCard(String userId, String collectionId) {
        CollectionEntity entry = collections.findById(collectionId).orElse(null);
        if (entry == null || !entry.getUserId().equals(userId)) {
            return false;
        }
        String cardId = entry.getCardId();
        String cardName = cards.findById(cardId).map(CardEntity::getName).orElse("");

        collections.deleteById(collectionId);
        events.publish(CardRemovedFromCollection.of(userId, cardId, cardName, collectionId));
        return true;
    }

    private static String normalize(String condition) {
        return (condition == null || condition.isBlank()) ? DEFAULT_CONDITION : condition;
    }
}
