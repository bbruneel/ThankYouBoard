package org.bruneel.thankyouboard.web.support;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds the current trace ID to the response as {@code X-Trace-Id} so clients
 * can correlate errors with traces and logs in Grafana (or any OTLP backend).
 * Only registered when a {@link Tracer} bean is present (e.g. full app with
 * OpenTelemetry); excluded from web slice tests that do not load tracing.
 */
@Component
@ConditionalOnBean(Tracer.class)
public class TraceIdFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public TraceIdFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = getTraceId();
        if (traceId != null) {
            response.setHeader("X-Trace-Id", traceId);
        }
        filterChain.doFilter(request, response);
    }

    private String getTraceId() {
        var context = tracer.currentTraceContext().context();
        return context != null ? context.traceId() : null;
    }
}
