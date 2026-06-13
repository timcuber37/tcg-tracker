package com.pokecollect.query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.pokecollect.query.cassandra.CollectionByUser;
import com.pokecollect.query.cassandra.CollectionByUserRepository;
import com.pokecollect.query.dto.CollectionCardDto;
import com.pokecollect.query.dto.CollectionResponse;

/**
 * Builds a user's collection view from the Cassandra collection_by_user read
 * model, overlaying current prices from the Postgres catalog and grouping
 * duplicate copies. Ports collection_view() from routes/query_routes.py.
 *
 * Live PokéWallet price fetch for un-priced cards is deferred to Phase 4.
 */
@Service
public class CollectionService {

    private final CollectionByUserRepository collectionRepo;
    private final CatalogSearchService catalog;

    public CollectionService(CollectionByUserRepository collectionRepo, CatalogSearchService catalog) {
        this.collectionRepo = collectionRepo;
        this.catalog = catalog;
    }

    public CollectionResponse forUser(String userId) {
        List<CollectionByUser> rows = collectionRepo.findByUserId(userId);

        // Overlay the latest catalog price (falls back to the price stored on the row).
        List<String> cardIds = rows.stream().map(CollectionByUser::getCardId).distinct().toList();
        Map<String, Double> livePrices = catalog.currentPrices(cardIds);

        // Group by card_id, preserving insertion order then sorting at the end.
        Map<String, Group> groups = new LinkedHashMap<>();
        for (CollectionByUser row : rows) {
            String cid = row.getCardId();
            Group g = groups.computeIfAbsent(cid, k -> {
                Double rowPrice = row.getMarketPriceUsd() == null ? null : row.getMarketPriceUsd().doubleValue();
                Double price = livePrices.getOrDefault(cid, rowPrice);
                if (price == null) price = rowPrice;
                return new Group(cid, row.getCardName(), row.getSetName(), row.getRarity(),
                                 row.getCondition(), price);
            });
            g.count++;
            g.collectionIds.add(row.getCollectionId());
        }

        List<CollectionCardDto> cards = groups.values().stream()
            .sorted(Comparator
                .comparing((Group g) -> g.setName == null ? "" : g.setName)
                .thenComparing(g -> g.cardName == null ? "" : g.cardName))
            .map(g -> new CollectionCardDto(g.cardId, g.cardName, g.setName, g.rarity,
                                            g.condition, g.price, g.count, g.collectionIds))
            .toList();

        int totalCopies = cards.stream().mapToInt(CollectionCardDto::count).sum();
        int pricedCopies = cards.stream()
            .filter(c -> c.marketPriceUsd() != null)
            .mapToInt(CollectionCardDto::count).sum();
        double totalValue = cards.stream()
            .filter(c -> c.marketPriceUsd() != null)
            .mapToDouble(c -> c.marketPriceUsd() * c.count()).sum();

        return new CollectionResponse(cards, totalCopies, pricedCopies, totalValue);
    }

    private static final class Group {
        final String cardId, cardName, setName, rarity, condition;
        final Double price;
        int count = 0;
        final List<String> collectionIds = new ArrayList<>();

        Group(String cardId, String cardName, String setName, String rarity, String condition, Double price) {
            this.cardId = cardId;
            this.cardName = cardName;
            this.setName = setName;
            this.rarity = rarity;
            this.condition = condition;
            this.price = price;
        }
    }
}
