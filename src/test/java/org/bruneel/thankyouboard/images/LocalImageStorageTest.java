package org.bruneel.thankyouboard.images;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageStorageTest {

    @Test
    void newObjectKey_usesPngExtensionForPng(@TempDir Path root) {
        LocalImageStorage storage = new LocalImageStorage(root);
        UUID boardId = UUID.randomUUID();
        String key = storage.newObjectKey(boardId, "image/png");
        assertThat(key).startsWith(boardId + "/").endsWith(".png");
    }

    @Test
    void newObjectKey_usesJpgExtensionForJpeg(@TempDir Path root) {
        LocalImageStorage storage = new LocalImageStorage(root);
        UUID boardId = UUID.randomUUID();
        String key = storage.newObjectKey(boardId, "image/jpeg");
        assertThat(key).endsWith(".jpg");
    }

    @Test
    void newObjectKey_emptyExtensionForUnknownType(@TempDir Path root) {
        LocalImageStorage storage = new LocalImageStorage(root);
        UUID boardId = UUID.randomUUID();
        String key = storage.newObjectKey(boardId, "image/gif");
        assertThat(key).doesNotContain(".gif");
    }

    @Test
    void resolvePath_rejectsPathTraversal(@TempDir Path root) {
        LocalImageStorage storage = new LocalImageStorage(root);
        assertThatThrownBy(() -> storage.resolvePath("../secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid object key");
    }

    @Test
    void save_writesBytesUnderRoot(@TempDir Path root) throws Exception {
        LocalImageStorage storage = new LocalImageStorage(root);
        UUID boardId = UUID.randomUUID();
        String key = boardId + "/file.png";
        byte[] data = new byte[] {1, 2, 3};
        storage.save(key, new ByteArrayInputStream(data));

        Path expected = root.resolve(key).normalize();
        assertThat(Files.readAllBytes(expected)).isEqualTo(data);
    }
}
