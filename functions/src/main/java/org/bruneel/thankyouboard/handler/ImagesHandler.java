package org.bruneel.thankyouboard.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.bruneel.thankyouboard.service.ImagesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;
import software.amazon.lambda.powertools.tracing.TracingUtils;

import java.util.Map;

public class ImagesHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(ImagesHandler.class);

    private final ImagesService imagesService;

    public ImagesHandler() {
        String bucket = System.getenv("IMAGE_BUCKET");
        String cdnBaseUrl = System.getenv("IMAGES_CDN_BASE_URL");
        int expires = parseExpiresSeconds(System.getenv("IMAGES_PRESIGN_EXPIRES_SECONDS"));
        this.imagesService = new ImagesService(bucket, cdnBaseUrl, expires);
    }

    @Override
    @Logging(clearState = true)
    @Tracing
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String correlationId = CorrelationContext.init(event, context);

        if (!ResponseUtil.validateAcceptHeader(event)) {
            APIGatewayProxyResponseEvent resp = ResponseUtil.jsonResponse(400,
                    Map.of("error", "Missing or invalid Accept header. Required: application/json; version=1"));
            ResponseUtil.stampCorrelationId(resp, correlationId);
            return resp;
        }

        String method = event.getHttpMethod();
        String path = event.getPath();
        log.info("Images request method={} path={}", method, path);

        APIGatewayProxyResponseEvent response;
        String boardId = null;
        try {
            if (!"POST".equals(method)) {
                log.warn("Images method not allowed. method={} path={}", method, path);
                response = ResponseUtil.jsonResponse(405, Map.of("error", "Method not allowed"));
            } else {
                Map<String, String> pathParams = event.getPathParameters();
                boardId = pathParams != null ? pathParams.get("id") : null;
                if (boardId == null) {
                    log.warn("boardId required in path for presign. path={}", path);
                    response = ResponseUtil.jsonResponse(400, Map.of("error", "boardId required in path"));
                } else {
                    MDC.put("board_id", boardId);
                    TracingUtils.putAnnotation("board_id", boardId);
                    response = imagesService.presign(boardId, event.getBody(), deriveBaseUrl(event));
                }
            }
        } catch (Exception e) {
            log.error("Error handling images request", e);
            response = ResponseUtil.jsonResponse(500,
                    Map.of("error", "Internal server error", "correlationId", correlationId));
        }

        ResponseUtil.stampCorrelationId(response, correlationId);
        MDC.put("status", String.valueOf(response.getStatusCode()));
        int status = response.getStatusCode();
        if (status >= 400 && status < 500) {
            log.info("Images response status={} path={} boardId={}", status, path, boardId);
        } else {
            log.info("Images response status={}", status);
        }
        return response;
    }

    private static String deriveBaseUrl(APIGatewayProxyRequestEvent event) {
        Map<String, String> headers = event == null ? null : event.getHeaders();
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        // Prefer the browser origin (CloudFront) over API Gateway host.
        // CloudFront forwards the viewer's Origin header, but replaces Host when proxying to API Gateway.
        String origin = firstHeader(headers, "origin");
        if (origin != null && !origin.isBlank()) {
            String normalized = origin.trim();
            // Origin is scheme + host (no path). Keep it as-is if it looks like an https origin.
            if (normalized.startsWith("https://") || normalized.startsWith("http://")) {
                stampBaseUrlObservability("origin", normalized);
                return normalized;
            }
        }

        // Fallback: derive from Referer (strip any path/query).
        String referer = firstHeader(headers, "referer", "referrer");
        if (referer != null && !referer.isBlank()) {
            String r = referer.trim();
            int idx = r.indexOf("://");
            if (idx > 0) {
                int start = 0;
                int slash = r.indexOf('/', idx + 3);
                if (slash > 0) {
                    r = r.substring(start, slash);
                }
                stampBaseUrlObservability("referer", r);
                return r;
            }
        }

        // Last resort: x-forwarded-host/host (will typically be execute-api when called through CloudFront).
        String host = firstHeader(headers, "x-forwarded-host", "host");
        if (host == null || host.isBlank()) {
            return null;
        }
        String proto = firstHeader(headers, "x-forwarded-proto");
        if (proto == null || proto.isBlank()) {
            proto = "https";
        }
        proto = proto.trim().toLowerCase();
        if (!"http".equals(proto) && !"https".equals(proto)) {
            proto = "https";
        }
        String baseUrl = proto + "://" + host.trim();
        stampBaseUrlObservability("host", baseUrl);
        return baseUrl;
    }

    private static void stampBaseUrlObservability(String source, String baseUrl) {
        String normalized = baseUrl == null ? null : baseUrl.trim();
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        String host = extractHost(normalized);
        if (host != null) {
            // Annotation is indexed and should be low-cardinality; host is fine, full URL is not.
            TracingUtils.putAnnotation("public_base_host", host);
        }
        TracingUtils.putMetadata("public_base_url", normalized);
        TracingUtils.putAnnotation("public_base_source", source);
        log.info("Images base URL derived source={} baseUrl={}", source, normalized);
    }

    private static String extractHost(String baseUrl) {
        String s = baseUrl == null ? "" : baseUrl.trim();
        int scheme = s.indexOf("://");
        if (scheme < 0) return null;
        int start = scheme + 3;
        if (start >= s.length()) return null;
        int slash = s.indexOf('/', start);
        String hostPort = slash > 0 ? s.substring(start, slash) : s.substring(start);
        int q = hostPort.indexOf('?');
        if (q >= 0) hostPort = hostPort.substring(0, q);
        int hash = hostPort.indexOf('#');
        if (hash >= 0) hostPort = hostPort.substring(0, hash);
        // Strip port if present
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0 && hostPort.indexOf(']') < 0) { // naive IPv6 guard
            hostPort = hostPort.substring(0, colon);
        }
        return hostPort.isBlank() ? null : hostPort;
    }

    private static String firstHeader(Map<String, String> headers, String... keysLowercase) {
        for (String key : keysLowercase) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static int parseExpiresSeconds(String raw) {
        int fallback = 600;
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < 60) return 60;
            if (parsed > 3600) return 3600;
            return parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

