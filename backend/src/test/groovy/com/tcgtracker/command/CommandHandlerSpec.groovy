package com.tcgtracker.command

import com.tcgtracker.command.domain.CardEntity
import com.tcgtracker.command.domain.CollectionEntity
import com.tcgtracker.command.repo.CardRepository
import com.tcgtracker.command.repo.CollectionRepository
import com.tcgtracker.events.CardAddedToCollection
import com.tcgtracker.events.CardRemovedFromCollection
import com.tcgtracker.events.EventPublisher
import com.tcgtracker.external.PriceEnrichmentService
import com.tcgtracker.query.CatalogSearchService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

/**
 * Write-side core: every command must persist to the MySQL source of truth (JPA)
 * AND publish the matching domain event so the read models can project it. These
 * specs mock the collaborators, so they assert that contract without live infra.
 */
class CommandHandlerSpec extends Specification {

    CardRepository cards = Mock()
    CollectionRepository collections = Mock()
    EventPublisher events = Mock()
    CatalogSearchService catalog = Mock()
    PriceEnrichmentService priceEnrichment = Mock()

    SimpleMeterRegistry metrics = new SimpleMeterRegistry()

    CommandHandler handler = new CommandHandler(cards, collections, events, catalog, priceEnrichment, metrics)

    def "addFromSearch persists a new card + collection row and publishes CardAddedToCollection"() {
        given: "a card not yet in the catalog and a caller-supplied price"
        cards.findById("pw-1") >> Optional.empty()
        CardAddedToCollection published = null

        when:
        String collectionId = handler.addFromSearch(
            "user-1", "pw-1", "Pikachu", "Base", "Rare", "Lightning", "Near Mint", 9.99d)

        then: "the catalog card is created (lazy) and the owned copy is saved"
        1 * cards.save({ CardEntity c -> c.cardId == "pw-1" && c.name == "Pikachu" })
        1 * collections.save({ CollectionEntity c -> c.userId == "user-1" && c.cardId == "pw-1" })

        and: "exactly one event is published (captured here so we can compare it to the returned id)"
        1 * events.publish(_ as CardAddedToCollection) >> { CardAddedToCollection e -> published = e }

        and: "no live price lookup happens when the caller already supplied one"
        0 * priceEnrichment.fetchAndCache(_)

        and: "the event carries the command's fields and the same collection id that was returned"
        published.userId == "user-1"
        published.cardId == "pw-1"
        published.cardName == "Pikachu"
        published.condition == "Near Mint"
        published.marketPriceUsd == 9.99d
        published.collectionId == collectionId
        published.eventType == "card_added_to_collection"
    }

    def "addFromSearch lazily enriches the price when the caller omits it"() {
        given: "an existing catalog card so no re-save happens"
        cards.findById("pw-2") >> Optional.of(new CardEntity("pw-2", "Mew", "Promo", "Rare", "Psychic", "pw-2"))

        when:
        handler.addFromSearch("user-1", "pw-2", "Mew", "Promo", "Rare", "Psychic", null, null)

        then: "the price is fetched once (response lives on the interaction) and rides the event"
        0 * cards.save(_)
        1 * priceEnrichment.fetchAndCache("pw-2") >> 42.50d
        1 * events.publish({ CardAddedToCollection e -> e.marketPriceUsd == 42.50d })
    }

    def "addFromSearch blanks a missing condition to the Near Mint default"() {
        given:
        cards.findById(_) >> Optional.empty()

        when:
        handler.addFromSearch("user-1", "pw-3", "Eevee", "Base", "Common", "Colorless", condition, 1.0d)

        then:
        1 * events.publish({ CardAddedToCollection e -> e.condition == "Near Mint" })

        where:
        condition << [null, "", "   "]
    }

    def "removeCard deletes the owned row and publishes CardRemovedFromCollection"() {
        given: "a collection row owned by the requesting user"
        collections.findById("col-1") >> Optional.of(new CollectionEntity("col-1", "user-1", "pw-1", "Near Mint"))
        cards.findById("pw-1") >> Optional.of(new CardEntity("pw-1", "Pikachu", "Base", "Rare", "Lightning", "pw-1"))

        when:
        boolean removed = handler.removeCard("user-1", "col-1")

        then:
        removed
        1 * collections.deleteById("col-1")
        1 * events.publish({ CardRemovedFromCollection e ->
            e.userId == "user-1" && e.cardId == "pw-1" &&
            e.cardName == "Pikachu" && e.collectionId == "col-1"
        })
    }

    def "removeCard is a no-op when the row belongs to a different user"() {
        given:
        collections.findById("col-1") >> Optional.of(new CollectionEntity("col-1", "owner", "pw-1", "Near Mint"))

        when:
        boolean removed = handler.removeCard("attacker", "col-1")

        then: "no delete, no event — ownership is enforced before any side effect"
        !removed
        0 * collections.deleteById(_)
        0 * events.publish(_)
    }

    def "removeCard is a no-op when the collection row does not exist"() {
        given:
        collections.findById("missing") >> Optional.empty()

        when:
        boolean removed = handler.removeCard("user-1", "missing")

        then:
        !removed
        0 * collections.deleteById(_)
        0 * events.publish(_)
    }

    def "handled commands increment the per-type tcg.commands counter"() {
        given:
        cards.findById(_) >> Optional.empty()
        collections.findById("col-1") >> Optional.of(new CollectionEntity("col-1", "user-1", "pw-1", "Near Mint"))

        when:
        handler.addFromSearch("user-1", "pw-1", "Pikachu", "Base", "Rare", "Lightning", "Near Mint", 1.0d)
        handler.removeCard("user-1", "col-1")

        then:
        metrics.counter("tcg.commands", "type", "add").count() == 1.0d
        metrics.counter("tcg.commands", "type", "remove").count() == 1.0d
    }
}
