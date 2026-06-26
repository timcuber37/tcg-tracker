package com.tcgtracker.external;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Google Cloud Vision OCR client (REST images:annotate). Sends a card photo and
 * returns the detected text + word bounding boxes as an {@link OcrResult}.
 * Mirrors the RestClient pattern of PokeWalletClient.
 */
@Component
public class VisionOcrClient {

    private static final Logger log = LoggerFactory.getLogger(VisionOcrClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient http = RestClient.create();
    private final String endpoint;
    private final String apiKey;

    public VisionOcrClient(
        @Value("${google.vision.endpoint:https://vision.googleapis.com/v1/images:annotate}") String endpoint,
        @Value("${google.vision.api-key:}") String apiKey
    ) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
    }

    /** OCR a card image. Throws on transport/API failure so the caller can surface it. */
    public OcrResult detect(byte[] imageBytes) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GOOGLE_VISION_API_KEY is not configured");
        }
        String request = buildRequest(imageBytes);
        String response;
        try {
            response = http.post()
                .uri(endpoint + "?key=" + apiKey)
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            log.error("Vision API error {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new IllegalStateException("Vision OCR request failed: HTTP " + e.getStatusCode().value(), e);
        }
        return parse(response);
    }

    private String buildRequest(byte[] imageBytes) {
        try {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode requests = root.putArray("requests");
            ObjectNode req = requests.addObject();
            req.putObject("image").put("content", b64);
            req.putArray("features").addObject().put("type", "DOCUMENT_TEXT_DETECTION");
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Vision request", e);
        }
    }

    /** Parse {responses[0]} into fullText + per-word boxes. textAnnotations[0] is the whole block. */
    OcrResult parse(String response) {
        try {
            JsonNode r0 = MAPPER.readTree(response == null ? "" : response).path("responses").path(0);
            if (r0.has("error")) {
                log.error("Vision response error: {}", r0.path("error").path("message").asText());
                return OcrResult.empty();
            }
            String fullText = r0.path("fullTextAnnotation").path("text")
                .asText(r0.path("textAnnotations").path(0).path("description").asText(""));

            List<OcrWord> words = new ArrayList<>();
            JsonNode anns = r0.path("textAnnotations");
            for (int i = 1; i < anns.size(); i++) { // skip [0] = whole-image text block
                JsonNode a = anns.get(i);
                String text = a.path("description").asText("");
                JsonNode verts = a.path("boundingPoly").path("vertices");
                if (text.isBlank() || !verts.isArray() || verts.isEmpty()) {
                    continue;
                }
                double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = 0, maxY = 0;
                for (JsonNode v : verts) {
                    double x = v.path("x").asDouble(0); // Vision omits zero coordinates
                    double y = v.path("y").asDouble(0);
                    minX = Math.min(minX, x); minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
                }
                words.add(new OcrWord(text, maxY - minY, minY, minX));
            }
            return new OcrResult(fullText, words);
        } catch (Exception e) {
            log.error("Failed to parse Vision response: {}", e.toString());
            return OcrResult.empty();
        }
    }
}
