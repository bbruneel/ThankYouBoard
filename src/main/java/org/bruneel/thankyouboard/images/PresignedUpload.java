package org.bruneel.thankyouboard.images;

public record PresignedUpload(
        String uploadUrl,
        String imageUrl,
        int expiresInSeconds,
        String contentType
) {
}

