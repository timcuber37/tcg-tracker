package com.tcgtracker.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tcgtracker.external.PokeWalletClient;

/**
 * Proxies + disk-caches card images so the PokéWallet API is hit at most once per
 * (card, size). The cache directory is a Docker volume in production, so fetched
 * images survive restarts. Ports routes/image_routes.py.
 */
@RestController
public class ImageController {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_]+$");

    private final PokeWalletClient pokewallet;
    private final Path cacheDir;

    public ImageController(PokeWalletClient pokewallet,
                           @Value("${image.cache-dir:static/cards}") String cacheDir) throws IOException {
        this.pokewallet = pokewallet;
        this.cacheDir = Path.of(cacheDir);
        Files.createDirectories(this.cacheDir);
    }

    @GetMapping("/card-image/{id}")
    public ResponseEntity<byte[]> cardImage(
        @PathVariable String id,
        @RequestParam(defaultValue = "low") String size
    ) throws IOException {
        if (!SAFE_ID.matcher(id).matches()) {
            return ResponseEntity.badRequest().build();
        }
        if (!size.equals("low") && !size.equals("high")) {
            size = "low";
        }

        Path cached = cacheDir.resolve(id + "_" + size + ".jpg");
        byte[] bytes;
        if (Files.exists(cached)) {
            bytes = Files.readAllBytes(cached);
        } else {
            bytes = pokewallet.getCardImageBytes(id, size);
            if (bytes == null || bytes.length == 0) {
                return ResponseEntity.notFound().build();
            }
            Files.write(cached, bytes);
        }

        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_JPEG)
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
            .body(bytes);
    }
}
