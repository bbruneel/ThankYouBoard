package org.bruneel.thankyouboard.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.bruneel.thankyouboard.handler.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class GiphyService {

    private static final Logger log = LoggerFactory.getLogger(GiphyService.class);
    private static final String GIPHY_API_BASE = "https://api.giphy.com/v1/gifs";
    private final String apiKey;

    public GiphyService(String apiKey) {
        this.apiKey = apiKey != null ? apiKey : "";
    }

    @Tracing(namespace = "GiphyService")
    public APIGatewayProxyResponseEvent search(String q, int limit, int offset) {
        if (apiKey.isEmpty()) {
            log.warn("Giphy search/trending unavailable: API key not set. Set GIPHY_API_KEY (or giphy.api-key) and redeploy.");
            return ResponseUtil.jsonResponse(503,
                    Map.of("error", "Giphy API key not configured. Set giphy.api-key or GIPHY_API_KEY."));
        }
        String uri = GIPHY_API_BASE + "/search?api_key=" + apiKey
                + "&q=" + urlEncode(q)
                + "&limit=" + limit
                + "&offset=" + offset;
        return fetchAndReturn(uri);
    }

    @Tracing(namespace = "GiphyService")
    public APIGatewayProxyResponseEvent trending(int limit, int offset) {
        if (apiKey.isEmpty()) {
            log.warn("Giphy search/trending unavailable: API key not set. Set GIPHY_API_KEY (or giphy.api-key) and redeploy.");
            return ResponseUtil.jsonResponse(503,
                    Map.of("error", "Giphy API key not configured. Set giphy.api-key or GIPHY_API_KEY."));
        }
        String uri = GIPHY_API_BASE + "/trending?api_key=" + apiKey
                + "&limit=" + limit
                + "&offset=" + offset;
        return fetchAndReturn(uri);
    }

    private APIGatewayProxyResponseEvent fetchAndReturn(String uri) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .GET()
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body();
            if (body == null) {
                body = "{\"data\":[],\"pagination\":{\"total_count\":0,\"count\":0,\"offset\":0}}";
            }
            APIGatewayProxyResponseEvent event = new APIGatewayProxyResponseEvent();
            event.setStatusCode(200);
            event.setHeaders(Map.of(
                    "Content-Type", "application/json",
                    "Access-Control-Allow-Origin", "*"
            ));
            event.setBody(body);
            return event;
        } catch (IOException | InterruptedException e) {
            log.warn("Giphy API unreachable: {}. Check network and Giphy status.", e.getMessage());
            return ResponseUtil.jsonResponse(502, Map.of("error", "Giphy API unreachable: " + e.getMessage()));
        }
    }

    private static String urlEncode(String s) {
        if (s == null) return "";
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
