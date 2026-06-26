package com.tcgtracker.web;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.tcgtracker.scan.ScanService;
import com.tcgtracker.scan.dto.ScanResponse;

/**
 * Card-scan endpoint. Accepts a webcam JPEG (multipart) and returns ranked catalog
 * candidates for the user to confirm. Auth-required (any authenticated user) — it
 * is enforced by the security config's {@code anyRequest().authenticated()} default,
 * since each call hits the paid Vision API.
 */
@RestController
@RequestMapping("/api")
public class ScanController {

    private final ScanService scan;

    public ScanController(ScanService scan) {
        this.scan = scan;
    }

    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScanResponse scan(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No image uploaded");
        }
        try {
            return scan.scan(file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded image", e);
        } catch (IllegalStateException e) {
            // Vision not configured / upstream failure.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }
}
