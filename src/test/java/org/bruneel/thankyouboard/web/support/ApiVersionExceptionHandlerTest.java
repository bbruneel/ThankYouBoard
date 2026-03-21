package org.bruneel.thankyouboard.web.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.accept.MissingApiVersionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApiVersionExceptionHandlerTest {

    private final ApiVersionExceptionHandler handler = new ApiVersionExceptionHandler();

    @Test
    void handleMissingApiVersion_returns415UnsupportedMediaType() {
        MissingApiVersionException ex = mock(MissingApiVersionException.class);

        ResponseEntity<Void> response = handler.handleMissingApiVersion(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNull();
    }
}
