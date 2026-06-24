package com.tcgtracker.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tcgtracker.query.CollectionService;
import com.tcgtracker.query.dto.CollectionResponse;

/** Authenticated collection view for the current user (JWT sub = user_id). */
@RestController
@RequestMapping("/api/collection")
public class CollectionController {

    private final CollectionService collection;

    public CollectionController(CollectionService collection) {
        this.collection = collection;
    }

    @GetMapping
    public CollectionResponse myCollection(@AuthenticationPrincipal Jwt jwt) {
        return collection.forUser(jwt.getSubject());
    }
}
