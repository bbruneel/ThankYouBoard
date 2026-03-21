package org.bruneel.thankyouboard.web;

import org.bruneel.thankyouboard.service.GiphyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/giphy", version = "1")
public class GiphyController {

    private static final Logger log = LoggerFactory.getLogger(GiphyController.class);

    private final GiphyService giphyService;

    public GiphyController(GiphyService giphyService) {
        this.giphyService = giphyService;
    }

    @GetMapping("/search")
    public ResponseEntity<String> search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        log.info("Giphy search: q='{}', limit={}, offset={}", q, limit, offset);
        if (!giphyService.isConfigured()) {
            log.warn("Giphy search requested but API key is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Giphy API key not configured. Set giphy.api-key or GIPHY_API_KEY.\"}");
        }
        String body = giphyService.search(q, limit, offset);
        log.debug("Giphy search returned {} chars", body != null ? body.length() : 0);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body != null ? body : "{\"data\":[],\"pagination\":{\"total_count\":0,\"count\":0,\"offset\":0}}");
    }

    @GetMapping("/trending")
    public ResponseEntity<String> trending(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        log.info("Giphy trending: limit={}, offset={}", limit, offset);
        if (!giphyService.isConfigured()) {
            log.warn("Giphy trending requested but API key is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Giphy API key not configured. Set giphy.api-key or GIPHY_API_KEY.\"}");
        }
        String body = giphyService.trending(limit, offset);
        log.debug("Giphy trending returned {} chars", body != null ? body.length() : 0);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body != null ? body : "{\"data\":[],\"pagination\":{\"total_count\":0,\"count\":0,\"offset\":0}}");
    }
}
