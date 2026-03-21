package org.bruneel.thankyouboard.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import org.bruneel.thankyouboard.handler.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.lambda.powertools.tracing.Tracing;
import software.amazon.lambda.powertools.tracing.TracingUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ImagesService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ImagesService.class);
    private static final Gson GSON = new Gson();

    private final String bucket;
    private final String fallbackBaseUrl;
    private final int expiresSeconds;
    private final S3Presigner presigner;

    public ImagesService(String bucket, String cdnBaseUrl, int expiresSeconds) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("IMAGE_BUCKET must be set");
        }
        this.bucket = bucket.trim();
        this.fallbackBaseUrl = normalizeBase(cdnBaseUrl);
        this.expiresSeconds = expiresSeconds;
        this.presigner = S3Presigner.create();
    }

    @Tracing(namespace = "ImagesService")
    public APIGatewayProxyResponseEvent presign(String boardIdStr, String body, String requestBaseUrl) {
        log.info("Image presign request received");
        if (boardIdStr == null || boardIdStr.isBlank()) {
            log.warn("Image presign rejected: missing boardId in path");
            return ResponseUtil.jsonResponse(400, Map.of("error", "boardId required (from path)"));
        }
        if (body == null || body.isBlank()) {
            log.warn("Image presign rejected: missing request body. boardId={}", boardIdStr);
            return ResponseUtil.jsonResponse(400, Map.of("error", "Request body required"));
        }
        UUID boardId;
        try {
            boardId = UUID.fromString(boardIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Image presign rejected: invalid boardId UUID. boardId={}", boardIdStr);
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid boardId UUID"));
        }
        TracingUtils.putAnnotation("board_id", boardId.toString());

        @SuppressWarnings("unchecked")
        Map<String, Object> json = GSON.fromJson(body, Map.class);
        String contentType = json.containsKey("contentType") ? (String) json.get("contentType") : null;
        Number len = json.containsKey("contentLengthBytes") ? (Number) json.get("contentLengthBytes") : null;
        long contentLengthBytes = len != null ? len.longValue() : -1L;

        String normalizedCt = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        if (!ImageUploadConstraints.ALLOWED_CONTENT_TYPES.contains(normalizedCt)) {
            log.warn("Image presign rejected: unsupported contentType. boardId={} contentType='{}'", boardId, normalizedCt);
            return ResponseUtil.jsonResponse(400, Map.of("error", "Unsupported contentType"));
        }
        if (contentLengthBytes < 0) {
            log.warn("Image presign rejected: missing contentLengthBytes. boardId={} contentType='{}'", boardId, normalizedCt);
            return ResponseUtil.jsonResponse(400, Map.of("error", "contentLengthBytes required"));
        }
        if (contentLengthBytes > ImageUploadConstraints.MAX_BYTES) {
            log.warn("Image presign rejected: image too large. boardId={} contentType='{}' sizeBytes={} maxBytes={}",
                    boardId, normalizedCt, contentLengthBytes, ImageUploadConstraints.MAX_BYTES);
            return ResponseUtil.jsonResponse(413, Map.of("error", "Image exceeds max size"));
        }

        String key = boardId + "/" + UUID.randomUUID() + extensionForContentType(normalizedCt);
        TracingUtils.putMetadata("image_key", key);
        TracingUtils.putAnnotation("content_type", normalizedCt);
        log.info("Image presign validated. boardId={} contentType='{}' sizeBytes={} key='images/{}'",
                boardId, normalizedCt, contentLengthBytes, key);

        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key("images/" + key)
                .contentType(normalizedCt)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiresSeconds))
                .putObjectRequest(put)
                .build());

        log.info("Image presign success. boardId={} bucket={} key='images/{}' expiresSeconds={} signedHeaders={}",
                boardId, bucket, key, expiresSeconds, presigned.signedHeaders().keySet());

        String baseUrl = normalizeBase(requestBaseUrl);
        if (baseUrl == null) {
            baseUrl = fallbackBaseUrl;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            log.error("Image presign cannot construct imageUrl: missing base URL. Set IMAGES_CDN_BASE_URL or call via CloudFront (/api/*).");
            return ResponseUtil.jsonResponse(500, Map.of("error", "Image CDN base URL not configured"));
        }

        TracingUtils.putMetadata("public_base_url", baseUrl);
        log.info("Image presign will return public image URL using baseUrl={}", baseUrl);

        String imageUrl = baseUrl + "/images/" + key;
        return ResponseUtil.jsonResponse(200, Map.of(
                "uploadUrl", presigned.url().toString(),
                "imageUrl", imageUrl,
                "expiresInSeconds", expiresSeconds,
                "contentType", normalizedCt
        ));
    }

    @Override
    public void close() {
        presigner.close();
    }

    private static String extensionForContentType(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> "";
        };
    }

    private static String normalizeBase(String base) {
        String b = base == null ? "" : base.trim();
        if (b.isBlank()) {
            return null;
        }
        if (b.endsWith("/")) {
            return b.substring(0, b.length() - 1);
        }
        return b;
    }
}

