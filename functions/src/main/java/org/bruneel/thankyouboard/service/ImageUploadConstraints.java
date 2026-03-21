package org.bruneel.thankyouboard.service;

import java.util.Set;

public final class ImageUploadConstraints {

    private ImageUploadConstraints() {}

    public static final long MAX_BYTES = 5L * 1024L * 1024L;

    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg"
    );
}

