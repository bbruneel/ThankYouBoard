package org.bruneel.thankyouboard.images;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
@Profile("!e2e")
@ConditionalOnProperty(value = "images.storage", havingValue = "local", matchIfMissing = true)
public class LocalImagePresignService implements ImagePresignService {

    private static final int DEFAULT_EXPIRES_SECONDS = 600;

    private final LocalImageStorage storage;
    private final String publicBaseUrl;
    private final int expiresInSeconds;

    public LocalImagePresignService(
            @Value("${images.local.root-dir:./.local-uploads}") String rootDir,
            @Value("${images.local.public-base-url:http://localhost:8080/uploads}") String publicBaseUrl,
            @Value("${images.presign.expires-seconds:" + DEFAULT_EXPIRES_SECONDS + "}") int expiresInSeconds
    ) {
        this.storage = new LocalImageStorage(Path.of(rootDir));
        this.publicBaseUrl = publicBaseUrl;
        this.expiresInSeconds = expiresInSeconds;
    }

    @Override
    public PresignedUpload presign(UUID boardId, String contentType, long contentLengthBytes) {
        validate(contentType, contentLengthBytes);
        String key = storage.newObjectKey(boardId, contentType);
        String uploadUrl = "/api/uploads/" + key;
        String imageUrl = normalizeBase(publicBaseUrl) + "/" + key;
        return new PresignedUpload(uploadUrl, imageUrl, expiresInSeconds, contentType);
    }

    LocalImageStorage storage() {
        return storage;
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

    private static String normalizeBase(String base) {
        String b = base == null ? "" : base.trim();
        if (b.endsWith("/")) {
            return b.substring(0, b.length() - 1);
        }
        return b;
    }
}

