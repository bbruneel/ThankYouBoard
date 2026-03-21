package org.bruneel.thankyouboard.images;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/boards/{boardId}/images", version = "1")
public class ImageUploadController {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadController.class);

    private final ImagePresignService presignService;

    public ImageUploadController(ImagePresignService presignService) {
        this.presignService = presignService;
    }

    @PostMapping("/presign")
    public ResponseEntity<?> presign(@PathVariable UUID boardId, @RequestBody Map<String, Object> body) {
        String contentType = body.containsKey("contentType") ? (String) body.get("contentType") : null;
        Number contentLength = body.containsKey("contentLengthBytes") ? (Number) body.get("contentLengthBytes") : null;
        long contentLengthBytes = contentLength != null ? contentLength.longValue() : -1L;
        try {
            log.info(
                    "Image upload presign requested: boardId={}, contentType='{}', contentLengthBytes={}",
                    boardId, contentType, contentLengthBytes
            );
            PresignedUpload presigned = presignService.presign(boardId, contentType, contentLengthBytes);
            log.info(
                    "Image upload presign success: boardId={}, uploadUrl='{}', imageUrl='{}', expiresInSeconds={}, contentType='{}'",
                    boardId, presigned.uploadUrl(), presigned.imageUrl(), presigned.expiresInSeconds(), presigned.contentType()
            );
            return ResponseEntity.ok(presigned);
        } catch (IllegalArgumentException ex) {
            log.warn(
                    "Image upload presign rejected: boardId={}, contentType='{}', contentLengthBytes={}, reason='{}'",
                    boardId, contentType, contentLengthBytes, ex.getMessage()
            );
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}

