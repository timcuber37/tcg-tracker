package com.tcgtracker.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tcgtracker.command.CommandHandler;
import com.tcgtracker.command.dto.AddCopyRequest;
import com.tcgtracker.command.dto.AddFromSearchRequest;
import com.tcgtracker.command.dto.RemoveCardRequest;

/**
 * Authenticated write-side endpoints. The acting user is always the JWT subject,
 * never a client-supplied id. Ports routes/command_routes.py.
 */
@RestController
@RequestMapping("/api/commands")
public class CommandController {

    private final CommandHandler handler;

    public CommandController(CommandHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/add-from-search")
    public Map<String, String> addFromSearch(@AuthenticationPrincipal Jwt jwt,
                                             @RequestBody AddFromSearchRequest req) {
        String collectionId = handler.addFromSearch(
            jwt.getSubject(), req.pokewalletId(), req.cardName(), req.setName(),
            req.rarity(), req.cardType(), req.condition(), req.marketPriceUsd());
        return Map.of("collectionId", collectionId);
    }

    @PostMapping("/add-copy")
    public ResponseEntity<Map<String, String>> addCopy(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestBody AddCopyRequest req) {
        String collectionId = handler.addCopy(jwt.getSubject(), req.pokewalletId(), req.condition());
        if (collectionId == null) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", "card not found in catalog"));
        }
        return ResponseEntity.ok(Map.of("collectionId", collectionId));
    }

    @PostMapping("/remove-card")
    public ResponseEntity<Void> removeCard(@AuthenticationPrincipal Jwt jwt,
                                          @RequestBody RemoveCardRequest req) {
        boolean removed = handler.removeCard(jwt.getSubject(), req.collectionId());
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
