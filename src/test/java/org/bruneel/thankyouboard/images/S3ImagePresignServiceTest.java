package org.bruneel.thankyouboard.images;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3ImagePresignServiceTest {

    @Mock
    private S3Presigner presigner;

    @Test
    void presign_returnsPresignedUrlAndCdnImageUrl() throws Exception {
        PresignedPutObjectRequest presignedPut = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        when(presignedPut.url()).thenReturn(
                java.net.URI.create("https://test-bucket.s3.amazonaws.com/board/x.png").toURL());
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedPut);

        S3ImagePresignService svc = new S3ImagePresignService(
                presigner,
                "my-bucket",
                "https://cdn.example.com/",
                300
        );
        UUID boardId = UUID.randomUUID();

        PresignedUpload u = svc.presign(boardId, "image/png", 1000);

        assertThat(u.uploadUrl()).startsWith("https://");
        assertThat(u.imageUrl()).startsWith("https://cdn.example.com/images/" + boardId);
        assertThat(u.expiresInSeconds()).isEqualTo(300);
        assertThat(u.contentType()).isEqualTo("image/png");
        verify(presigner).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void presign_rejectsInvalidContentType() {
        S3ImagePresignService svc = new S3ImagePresignService(
                presigner,
                "b",
                "https://cdn.example.com",
                60
        );
        assertThatThrownBy(() -> svc.presign(UUID.randomUUID(), "image/gif", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
