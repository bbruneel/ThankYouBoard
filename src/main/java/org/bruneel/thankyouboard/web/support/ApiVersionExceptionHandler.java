package org.bruneel.thankyouboard.web.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.accept.MissingApiVersionException;

@RestControllerAdvice
public class ApiVersionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionExceptionHandler.class);

    /**
     * When Accept does not specify an API version (e.g. "Accept: application/json"),
     * return 415 Unsupported Media Type as requested.
     */
    @ExceptionHandler(MissingApiVersionException.class)
    public ResponseEntity<Void> handleMissingApiVersion(MissingApiVersionException ex) {
        log.warn("Request missing API version in Accept header: returning 415 Unsupported Media Type");
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
    }
}
