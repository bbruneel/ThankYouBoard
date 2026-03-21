package org.bruneel.thankyouboard.images;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(value = "images.storage", havingValue = "s3")
public class S3ImagePresignService implements ImagePresignService, AutoCloseable {

    private final String bucket;
    private final String cdnBaseUrl;
    private final int expiresInSeconds;
    private final S3Presigner presigner;

    public S3ImagePresignService(
            @Value("${images.s3.bucket}") String bucket,
            @Value("${images.cdn.base-url}") String cdnBaseUrl,
            @Value("${images.presign.expires-seconds:600}") int expiresInSeconds,
            @Value("${images.s3.region:}") String region
    ) {
        this.bucket = bucket;
        this.cdnBaseUrl = normalizeBase(cdnBaseUrl);
        this.expiresInSeconds = expiresInSeconds;
        S3Presigner.Builder builder = S3Presigner.builder();
        if (region != null && !region.isBlank()) {
            builder.region(Region.of(region.trim()));
        }
        this.presigner = builder.build();
    }

    @Override
    public PresignedUpload presign(UUID boardId, String contentType, long contentLengthBytes) {
        validate(contentType, contentLengthBytes);

        String key = boardId + "/" + UUID.randomUUID() + extensionForContentType(contentType);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                // Keep ACL private; reads should be via CloudFront (or signed URLs later).
                .acl(ObjectCannedACL.PRIVATE)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                .putObjectRequest(objectRequest)
                .build());

        String imageUrl = cdnBaseUrl + "/images/" + key;
        return new PresignedUpload(presigned.url().toString(), imageUrl, expiresInSeconds, contentType);
    }

    @Override
    public void close() {
        presigner.close();
    }

    private static void validate(String contentType, long contentLengthBytes) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        if (!ImageUploadConstraints.ALLOWED_CONTENT_TYPES.contains(ct)) {
            throw new IllegalArgumentException("Unsupported contentType. Allowed: " + ImageUploadConstraints.ALLOWED_CONTENT_TYPES);
        }
        if (contentLengthBytes < 0) {
            throw new IllegalArgumentException("contentLengthBytes must be provided");
        }
        if (contentLengthBytes > ImageUploadConstraints.MAX_BYTES) {
            throw new IllegalArgumentException("Image exceeds max size (" + ImageUploadConstraints.MAX_BYTES + " bytes)");
        }
    }

    private static String extensionForContentType(String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        return switch (ct) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> "";
        };
    }

    private static String normalizeBase(String base) {
        String b = base == null ? "" : base.trim();
        if (b.endsWith("/")) {
            return b.substring(0, b.length() - 1);
        }
        return b;
    }
}

