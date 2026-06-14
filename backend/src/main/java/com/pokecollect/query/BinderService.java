package com.pokecollect.query;

import java.util.List;

import org.springframework.stereotype.Service;

import com.pokecollect.query.cassandra.BinderByUser;
import com.pokecollect.query.cassandra.BinderByUserRepository;
import com.pokecollect.query.dto.BinderResponse;
import com.pokecollect.query.dto.BinderSlotDto;

/** Reads a user's binder from the Cassandra binder_by_user read model. */
@Service
public class BinderService {

    private final BinderByUserRepository binderRepo;

    public BinderService(BinderByUserRepository binderRepo) {
        this.binderRepo = binderRepo;
    }

    public BinderResponse forUser(String userId) {
        List<BinderByUser> rows = binderRepo.findByUserId(userId);
        List<BinderSlotDto> slots = rows.stream()
            .map(r -> new BinderSlotDto(r.getPageNumber(), r.getSlotIndex(),
                                        r.getCardId(), r.getCardName(), r.getSetName(), r.getRarity()))
            .toList();
        // Number of used pages (0-indexed → max + 1); 0 when the binder is empty.
        // The frontend offers one blank page beyond this to fill.
        int pageCount = rows.stream().mapToInt(BinderByUser::getPageNumber).max().orElse(-1) + 1;
        return new BinderResponse(slots, pageCount);
    }
}
