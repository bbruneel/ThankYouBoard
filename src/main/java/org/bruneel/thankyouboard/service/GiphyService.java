package org.bruneel.thankyouboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GiphyService {

    private static final Logger log = LoggerFactory.getLogger(GiphyService.class);

    private final RestClient restClient;
    private final String apiKey;

    public GiphyService(
            RestClient.Builder restClientBuilder,
            @Value("${giphy.api-key:}") String apiKey) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        if (this.apiKey.isEmpty()) {
            log.info("Giphy API key not set; GIF search and trending will return 503 until configured");
        } else {
            log.debug("Giphy API key configured");
        }
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /**
     * Proxies Giphy search. Returns JSON response body or null if key not configured.
     */
    @Retryable(maxRetries = 3, delay = 100, multiplier = 2, maxDelay = 1000)
    public String search(String q, int limit, int offset) {
        if (!isConfigured()) return null;
        log.debug("Calling Giphy API: search q='{}'", q);
        return restClient.get()
                .uri(builder -> builder
                        .scheme("https")
                        .host("api.giphy.com")
                        .path("/v1/gifs/search")
                        .queryParam("api_key", apiKey)
                        .queryParam("q", q != null ? q : "")
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }

    /**
     * Proxies Giphy trending. Returns JSON response body or null if key not configured.
     */
    @Retryable(maxRetries = 3, delay = 100, multiplier = 2, maxDelay = 1000)
    public String trending(int limit, int offset) {
        if (!isConfigured()) return null;
        log.debug("Calling Giphy API: trending");
        return restClient.get()
                .uri(builder -> builder
                        .scheme("https")
                        .host("api.giphy.com")
                        .path("/v1/gifs/trending")
                        .queryParam("api_key", apiKey)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }
}
