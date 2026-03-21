package org.bruneel.thankyouboard.images;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

@RestController
@Profile("e2e")
@RequestMapping("/api/uploads")
public class E2eLocalUploadPutController {

    private final E2eLocalImagePresignService presignService;

    public E2eLocalUploadPutController(E2eLocalImagePresignService presignService) {
        this.presignService = presignService;
    }

    @PutMapping("/{boardId}/{objectName}")
    public ResponseEntity<?> put(
            @PathVariable String boardId,
            @PathVariable String objectName,
            HttpServletRequest request
    ) {
        String contentType = request.getContentType();
        String normalized = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        if (!ImageUploadConstraints.ALLOWED_CONTENT_TYPES.contains(normalized)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported Content-Type"));
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > ImageUploadConstraints.MAX_BYTES) {
            return ResponseEntity.status(413).body(Map.of("error", "Image exceeds max size"));
        }
        if (contentLength < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content-Length required"));
        }
        String key = boardId + "/" + objectName;
        try (InputStream in = request.getInputStream()) {
            presignService.storage().save(key, in);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IOException ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to store image"));
        }
    }
}

