package org.bruneel.thankyouboard.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.bruneel.thankyouboard.service.GiphyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.util.Map;

public class GiphyHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(GiphyHandler.class);
    private final GiphyService giphyService;

    public GiphyHandler() {
        String giphyApiKey = System.getenv("GIPHY_API_KEY");
        this.giphyService = new GiphyService(giphyApiKey != null ? giphyApiKey.trim() : "");
    }

    @Override
    @Logging(clearState = true)
    @Tracing
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String correlationId = CorrelationContext.init(event, context);

        String path = event.getPath();
        log.info("Giphy GET {}", path);

        if (!ResponseUtil.validateAcceptHeader(event)) {
            APIGatewayProxyResponseEvent resp = ResponseUtil.jsonResponse(400,
                    Map.of("error", "Missing or invalid Accept header. Required: application/json; version=1"));
            ResponseUtil.stampCorrelationId(resp, correlationId);
            return resp;
        }

        Map<String, String> qs = event.getQueryStringParameters();
        String q = qs != null ? qs.getOrDefault("q", qs.get("q")) : null;
        int limit = parseInt(qs != null ? qs.get("limit") : null, 10);
        int offset = parseInt(qs != null ? qs.get("offset") : null, 0);

        limit = Math.max(1, Math.min(50, limit));
        offset = Math.max(0, offset);

        APIGatewayProxyResponseEvent response;
        try {
            if (path != null && path.endsWith("/search")) {
                response = giphyService.search(q != null ? q : "", limit, offset);
            } else if (path != null && path.endsWith("/trending")) {
                response = giphyService.trending(limit, offset);
            } else {
                log.warn("Giphy path not found: '{}'. Use /search or /trending.", path);
                response = ResponseUtil.jsonResponse(404, Map.of("error", "Not found"));
            }
        } catch (Exception e) {
            log.error("Error handling giphy request", e);
            response = ResponseUtil.jsonResponse(500,
                    Map.of("error", "Internal server error", "correlationId", correlationId));
        }

        ResponseUtil.stampCorrelationId(response, correlationId);
        MDC.put("status", String.valueOf(response.getStatusCode()));
        log.info("Giphy response status={}", response.getStatusCode());
        return response;
    }

    private static int parseInt(String s, int defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
