package com.pokecollect.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pokecollect.query.CatalogSearchService;
import com.pokecollect.query.MarketService;
import com.pokecollect.query.dto.CardDto;
import com.pokecollect.query.dto.SearchResponse;

/**
 * Read-side REST endpoints. Catalog search is backed by Supabase Postgres;
 * "browse by set" is backed by the Cassandra cards_by_set read model.
 * Ports the read routes from routes/query_routes.py.
 */
@RestController
@RequestMapping("/api")
public class QueryController {

    private final CatalogSearchService catalog;
    private final MarketService market;

    public QueryController(CatalogSearchService catalog, MarketService market) {
        this.catalog = catalog;
        this.market = market;
    }

    @GetMapping("/search")
    public SearchResponse search(
        @RequestParam(name = "q", defaultValue = "") String query,
        @RequestParam(name = "set", defaultValue = "") String setName,
        @RequestParam(name = "page", defaultValue = "1") int page
    ) {
        int safePage = Math.max(1, page);
        return catalog.search(query.strip(), setName.strip(), safePage);
    }

    @GetMapping("/sets")
    public List<String> sets(@RequestParam(name = "q", defaultValue = "") String query) {
        return catalog.setNames(query.strip());
    }

    @GetMapping("/rare-cards")
    public List<String> rareCards() {
        return catalog.rareCardIds(60);
    }

    /** Browse all cards in a given set (Cassandra read model). */
    @GetMapping("/market")
    public List<CardDto> market(@RequestParam(name = "set", defaultValue = "") String setName) {
        if (setName.isBlank()) {
            return List.of();
        }
        return market.cardsInSet(setName);
    }

    /** All set names present in the Cassandra catalog. */
    @GetMapping("/market/sets")
    public List<String> marketSets() {
        return market.allSetNames();
    }
}
