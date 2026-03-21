package org.bruneel.thankyouboard.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public final class ResponseUtil {

    private static final Logger log = LoggerFactory.getLogger(ResponseUtil.class);
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
            .create();

    private ResponseUtil() {}

    public static APIGatewayProxyResponseEvent jsonResponse(int status, Object body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(status);
        response.setHeaders(new HashMap<>(Map.of(
                "Content-Type", "application/json",
                "Access-Control-Allow-Origin", "*"
        )));
        response.setBody(GSON.toJson(body));
        return response;
    }

    /** Returns a 204 No Content response with no body (RFC 9110 §15.3.5). */
    public static APIGatewayProxyResponseEvent noContentResponse() {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(204);
        response.setHeaders(new HashMap<>(Map.of(
                "Access-Control-Allow-Origin", "*"
        )));
        response.setBody(null);
        return response;
    }

    /**
     * Stamps the correlation / request ID onto every response so the caller
     * can match it with CloudWatch / X-Ray.
     */
    public static void stampCorrelationId(APIGatewayProxyResponseEvent response, String correlationId) {
        if (response == null || correlationId == null) return;

        Map<String, String> existingHeaders = response.getHeaders();
        Map<String, String> mutableHeaders;

        if (existingHeaders == null) {
            mutableHeaders = new HashMap<>();
        } else {
            // Ensure we always work with a mutable map since some callers may
            // have set an immutable headers map (e.g. via Map.of(...)).
            mutableHeaders = new HashMap<>(existingHeaders);
        }

        mutableHeaders.put(CorrelationContext.RESPONSE_HEADER_REQUEST_ID, correlationId);
        response.setHeaders(mutableHeaders);
    }

    public static boolean validateAcceptHeader(APIGatewayProxyRequestEvent event) {
        Map<String, String> headers = event.getHeaders();
        String accept = headers != null ? headers.getOrDefault("Accept", headers.get("accept")) : null;
        boolean valid = accept != null && accept.contains("version=1");
        if (!valid) {
            String path = event.getPath() != null ? event.getPath() : "(unknown)";
            String acceptValue = accept != null ? accept : "missing";
            log.warn("Invalid or missing Accept header for path '{}'. Received: {}. Required: Accept: application/json; version=1", path, acceptValue);
        }
        return valid;
    }
}
