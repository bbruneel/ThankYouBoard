package org.bruneel.thankyouboard.images;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

@RestController
@Profile("!e2e")
@ConditionalOnProperty(value = "images.storage", havingValue = "local", matchIfMissing = true)
@RequestMapping("/api/uploads")
public class LocalUploadPutController {

    private static final Logger log = LoggerFactory.getLogger(LocalUploadPutController.class);

    private final LocalImagePresignService presignService;

    public LocalUploadPutController(LocalImagePresignService presignService) {
        this.presignService = presignService;
    }

    @PutMapping("/{boardId}/{objectName}")
    public ResponseEntity<?> put(
            @PathVariable String boardId,
            @PathVariable String objectName,
            HttpServletRequest request
    ) {
        String key = boardId + "/" + objectName;
        String contentType = request.getContentType();
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        if (!ImageUploadConstraints.ALLOWED_CONTENT_TYPES.contains(normalized)) {
            log.warn("Image upload rejected (unsupported content type): key='{}', contentType='{}'", key, contentType);
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported Content-Type"));
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > ImageUploadConstraints.MAX_BYTES) {
            log.warn(
                    "Image upload rejected (too large): key='{}', contentLengthBytes={}, maxBytes={}",
                    key, contentLength, ImageUploadConstraints.MAX_BYTES
            );
            return ResponseEntity.status(413).body(Map.of("error", "Image exceeds max size"));
        }
        log.info(
                "Image upload PUT received: key='{}', contentType='{}', contentLengthBytes={}",
                key, contentType, contentLength
        );
        try (InputStream in = request.getInputStream()) {
            presignService.storage().save(key, in);
            log.info("Image upload stored successfully: key='{}'", key);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            log.warn("Image upload rejected: key='{}', reason='{}'", key, ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IOException ex) {
            log.error("Image upload failed storing image: key='{}'", key, ex);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to store image"));
        }
    }
}

