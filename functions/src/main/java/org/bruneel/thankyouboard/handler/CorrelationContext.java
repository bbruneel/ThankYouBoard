package org.bruneel.thankyouboard.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.slf4j.MDC;
import software.amazon.lambda.powertools.tracing.TracingUtils;

import java.util.Map;
import java.util.UUID;

/**
 * Shared helper that extracts or generates a per-request correlation ID
 * and pushes it (plus other request-scoped IDs) into the SLF4J MDC
 * (which Powertools Log4j2 layout includes in structured JSON output)
 * and X-Ray annotations.
 */
public final class CorrelationContext {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String RESPONSE_HEADER_REQUEST_ID = "x-request-id";

    private CorrelationContext() {}

    /**
     * Initialise the logging/tracing context for an API Gateway request.
     * Returns the resolved correlation ID so the handler can echo it back.
     */
    public static String init(APIGatewayProxyRequestEvent event, Context lambdaContext) {
        String correlationId = extractHeader(event, HEADER_REQUEST_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("correlation_id", correlationId);
        MDC.put("http_method", event.getHttpMethod());
        MDC.put("path", event.getPath());

        if (lambdaContext != null) {
            MDC.put("aws_request_id", lambdaContext.getAwsRequestId());
        }

        String apiRequestId = extractApiGatewayRequestId(event);
        if (apiRequestId != null) {
            MDC.put("api_request_id", apiRequestId);
        }

        String xrayTraceId = parseXRayTraceId();
        if (xrayTraceId != null) {
            MDC.put("xray_trace_id", xrayTraceId);
        }

        TracingUtils.putAnnotation("correlation_id", correlationId);

        return correlationId;
    }

    /**
     * Initialise the logging/tracing context for an SQS-triggered invocation.
     * The correlation ID is expected in the SQS message attributes; falls back
     * to a new UUID.
     */
    public static String initFromSqs(String correlationId, Context lambdaContext) {
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put("correlation_id", correlationId);

        if (lambdaContext != null) {
            MDC.put("aws_request_id", lambdaContext.getAwsRequestId());
        }

        String xrayTraceId = parseXRayTraceId();
        if (xrayTraceId != null) {
            MDC.put("xray_trace_id", xrayTraceId);
        }

        TracingUtils.putAnnotation("correlation_id", correlationId);

        return correlationId;
    }

    private static String extractHeader(APIGatewayProxyRequestEvent event, String name) {
        Map<String, String> headers = event.getHeaders();
        if (headers == null) return null;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String extractApiGatewayRequestId(APIGatewayProxyRequestEvent event) {
        if (event.getRequestContext() == null) return null;
        return event.getRequestContext().getRequestId();
    }

    /**
     * Parses the root trace ID from the {@code _X_AMZN_TRACE_ID} environment
     * variable (format: {@code Root=1-xxx;Parent=yyy;Sampled=1}).
     */
    static String parseXRayTraceId() {
        String traceEnv = System.getenv("_X_AMZN_TRACE_ID");
        if (traceEnv == null || traceEnv.isBlank()) return null;
        for (String part : traceEnv.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("Root=")) {
                return trimmed.substring(5);
            }
        }
        return traceEnv;
    }
}
