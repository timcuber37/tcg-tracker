package com.pokecollect.command;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pokecollect.command.domain.BinderSlotEntity;
import com.pokecollect.command.domain.CardEntity;
import com.pokecollect.command.repo.BinderSlotRepository;
import com.pokecollect.command.repo.CardRepository;
import com.pokecollect.command.repo.CollectionRepository;
import com.pokecollect.events.CardPlacedInBinder;
import com.pokecollect.events.CardRemovedFromBinder;
import com.pokecollect.events.EventPublisher;

/**
 * Write side for the binder. Persists slot placement to MySQL and publishes a
 * Kafka event so the consumer projects it into the binder_by_user read model.
 * A card may only be placed if the user owns it (it appears in their collection).
 */
@Service
public class BinderCommandHandler {

    private static final int SLOTS_PER_PAGE = 9;

    private final BinderSlotRepository slots;
    private final CardRepository cards;
    private final CollectionRepository collections;
    private final EventPublisher events;

    public BinderCommandHandler(BinderSlotRepository slots, CardRepository cards,
                                CollectionRepository collections, EventPublisher events) {
        this.slots = slots;
        this.cards = cards;
        this.collections = collections;
        this.events = events;
    }

    /** Place a card in a pocket. Returns false if invalid, not owned, or no copy is available. */
    @Transactional
    public boolean place(String userId, String cardId, int pageNumber, int slotIndex) {
        if (pageNumber < 0 || slotIndex < 0 || slotIndex >= SLOTS_PER_PAGE) {
            return false;
        }
        CardEntity card = cards.findById(cardId).orElse(null);
        if (card == null) {
            return false;
        }
        long owned = collections.countByUserIdAndCardId(userId, cardId);
        if (owned == 0) {
            return false; // not owned
        }

        BinderSlotEntity existing =
            slots.findByUserIdAndPageNumberAndSlotIndex(userId, pageNumber, slotIndex).orElse(null);
        boolean sameCardAlreadyHere = existing != null && existing.getCardId().equals(cardId);

        // Adding this card consumes a copy unless the pocket already holds it.
        // Block if every owned copy is already placed somewhere in the binder.
        if (!sameCardAlreadyHere && slots.countByUserIdAndCardId(userId, cardId) >= owned) {
            return false; // no available copies
        }

        // One card per pocket: clear any existing occupant, then insert.
        if (existing != null) {
            slots.delete(existing);
        }
        slots.save(new BinderSlotEntity(UUID.randomUUID().toString(), userId, pageNumber, slotIndex, cardId));

        events.publish(CardPlacedInBinder.of(
            userId, cardId, card.getName(), card.getSetName(), card.getRarity(), pageNumber, slotIndex));
        return true;
    }

    /** Clear a pocket. Returns false if it was already empty. */
    @Transactional
    public boolean remove(String userId, int pageNumber, int slotIndex) {
        BinderSlotEntity existing =
            slots.findByUserIdAndPageNumberAndSlotIndex(userId, pageNumber, slotIndex).orElse(null);
        if (existing == null) {
            return false;
        }
        slots.delete(existing);
        events.publish(CardRemovedFromBinder.of(userId, pageNumber, slotIndex));
        return true;
    }
}
