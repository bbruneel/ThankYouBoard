package org.bruneel.thankyouboard.web.support;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TraceIdFilterTest {

    @Test
    void addsXTraceIdWhenTraceContextPresent() throws Exception {
        TraceContext traceContext = mock(TraceContext.class);
        when(traceContext.traceId()).thenReturn("deadbeefdeadbeefdeadbeefdeadbeef");

        CurrentTraceContext currentTraceContext = mock(CurrentTraceContext.class);
        when(currentTraceContext.context()).thenReturn(traceContext);

        Tracer tracer = mock(Tracer.class);
        when(tracer.currentTraceContext()).thenReturn(currentTraceContext);

        TraceIdFilter filter = new TraceIdFilter(tracer);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("deadbeefdeadbeefdeadbeefdeadbeef");
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotSetHeaderWhenTraceContextNull() throws Exception {
        CurrentTraceContext currentTraceContext = mock(CurrentTraceContext.class);
        when(currentTraceContext.context()).thenReturn(null);

        Tracer tracer = mock(Tracer.class);
        when(tracer.currentTraceContext()).thenReturn(currentTraceContext);

        TraceIdFilter filter = new TraceIdFilter(tracer);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isNull();
        verify(chain).doFilter(request, response);
    }
}
