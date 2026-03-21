package org.bruneel.thankyouboard.images;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImagePresignServiceTest {

    @Test
    void presign_returnsUploadPathAndImageUrl(@TempDir Path root) {
        LocalImagePresignService svc = new LocalImagePresignService(
                root.toString(),
                "http://localhost:8080/uploads",
                600
        );
        UUID boardId = UUID.randomUUID();

        PresignedUpload u = svc.presign(boardId, "image/png", 1024);

        assertThat(u.uploadUrl()).startsWith("/api/uploads/" + boardId);
        assertThat(u.uploadUrl()).endsWith(".png");
        assertThat(u.imageUrl()).startsWith("http://localhost:8080/uploads/");
        assertThat(u.expiresInSeconds()).isEqualTo(600);
        assertThat(u.contentType()).isEqualTo("image/png");
    }

    @Test
    void presign_stripsTrailingSlashFromPublicBase(@TempDir Path root) {
        LocalImagePresignService svc = new LocalImagePresignService(
                root.toString(),
                "http://example.com/uploads/",
                60
        );
        PresignedUpload u = svc.presign(UUID.randomUUID(), "image/jpeg", 1);
        String key = u.uploadUrl().substring("/api/uploads/".length());
        assertThat(u.imageUrl()).isEqualTo("http://example.com/uploads/" + key);
    }

    @Test
    void presign_rejectsUnsupportedContentType(@TempDir Path root) {
        LocalImagePresignService svc = new LocalImagePresignService(root.toString(), "http://localhost/", 600);
        assertThatThrownBy(() -> svc.presign(UUID.randomUUID(), "image/gif", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported contentType");
    }

    @Test
    void presign_rejectsNegativeContentLength(@TempDir Path root) {
        LocalImagePresignService svc = new LocalImagePresignService(root.toString(), "http://localhost/", 600);
        assertThatThrownBy(() -> svc.presign(UUID.randomUUID(), "image/png", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentLengthBytes");
    }

    @Test
    void presign_rejectsOversized(@TempDir Path root) {
        LocalImagePresignService svc = new LocalImagePresignService(root.toString(), "http://localhost/", 600);
        assertThatThrownBy(() -> svc.presign(UUID.randomUUID(), "image/png", ImageUploadConstraints.MAX_BYTES + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max size");
    }
}
