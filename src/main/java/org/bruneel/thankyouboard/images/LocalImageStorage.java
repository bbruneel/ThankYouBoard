package org.bruneel.thankyouboard.images;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

final class LocalImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalImageStorage.class);

    private final Path root;

    LocalImageStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        log.info("Local image storage initialized: rootDir='{}'", this.root);
    }

    String newObjectKey(UUID boardId, String contentType) {
        String ext = extensionForContentType(contentType);
        return boardId + "/" + UUID.randomUUID() + ext;
    }

    Path resolvePath(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            log.warn(
                    "Invalid local upload object key rejected: objectKey='{}', resolvedTarget='{}', rootDir='{}'",
                    objectKey, target, root
            );
            throw new IllegalArgumentException("Invalid object key");
        }
        return target;
    }

    void save(String objectKey, InputStream in) throws IOException {
        Path target = resolvePath(objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String extensionForContentType(String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        return switch (ct) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> "";
        };
    }
}

