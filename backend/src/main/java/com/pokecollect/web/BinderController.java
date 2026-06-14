package com.pokecollect.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pokecollect.command.BinderCommandHandler;
import com.pokecollect.command.dto.PlaceCardRequest;
import com.pokecollect.command.dto.RemoveSlotRequest;
import com.pokecollect.query.BinderService;
import com.pokecollect.query.dto.BinderResponse;

/** Authenticated binder endpoints for the current user (JWT sub = user_id). */
@RestController
@RequestMapping("/api/binder")
public class BinderController {

    private final BinderService binderService;
    private final BinderCommandHandler handler;

    public BinderController(BinderService binderService, BinderCommandHandler handler) {
        this.binderService = binderService;
        this.handler = handler;
    }

    @GetMapping
    public BinderResponse myBinder(@AuthenticationPrincipal Jwt jwt) {
        return binderService.forUser(jwt.getSubject());
    }

    @PostMapping("/place")
    public ResponseEntity<Map<String, String>> place(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestBody PlaceCardRequest req) {
        boolean ok = handler.place(jwt.getSubject(), req.cardId(), req.pageNumber(), req.slotIndex());
        return ok
            ? ResponseEntity.ok(Map.of("status", "placed"))
            : ResponseEntity.unprocessableEntity().body(Map.of("error", "invalid slot or card not owned"));
    }

    @PostMapping("/remove")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt,
                                       @RequestBody RemoveSlotRequest req) {
        boolean ok = handler.remove(jwt.getSubject(), req.pageNumber(), req.slotIndex());
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
