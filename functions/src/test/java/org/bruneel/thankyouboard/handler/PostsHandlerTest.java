package org.bruneel.thankyouboard.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PostsHandlerTest {

    @Test
    void extractCapabilityToken_isCaseInsensitive() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHeaders(Map.of("x-post-capability-token", "  abc123  "));

        assertEquals("abc123", PostsHandler.extractCapabilityToken(event));
    }

    @Test
    void extractCapabilityToken_returnsNullWhenMissingOrBlank() {
        APIGatewayProxyRequestEvent missing = new APIGatewayProxyRequestEvent();
        missing.setHeaders(Map.of("other", "value"));
        assertNull(PostsHandler.extractCapabilityToken(missing));

        APIGatewayProxyRequestEvent blank = new APIGatewayProxyRequestEvent();
        blank.setHeaders(Map.of("X-Post-Capability-Token", "   "));
        assertNull(PostsHandler.extractCapabilityToken(blank));
    }
}

