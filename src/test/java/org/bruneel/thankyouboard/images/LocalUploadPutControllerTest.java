package org.bruneel.thankyouboard.images;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocalUploadPutControllerTest {

    @TempDir
    Path tempDir;

    private MockMvc mockMvc;
    private LocalUploadPutController controller;

    @BeforeEach
    void setUp() {
        LocalImagePresignService presign = new LocalImagePresignService(
                tempDir.toString(),
                "http://localhost:8080/uploads",
                600
        );
        controller = new LocalUploadPutController(presign);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void put_storesPngBytes() throws Exception {
        UUID boardId = UUID.randomUUID();
        String objectName = "obj.png";
        byte[] body = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47};

        mockMvc.perform(put("/api/uploads/" + boardId + "/" + objectName)
                        .contentType("image/png")
                        .content(body))
                .andExpect(status().isOk());

        Path stored = tempDir.resolve(boardId.toString()).resolve(objectName);
        assertThat(Files.readAllBytes(stored)).isEqualTo(body);
    }

    @Test
    void put_rejectsUnsupportedContentType() throws Exception {
        mockMvc.perform(put("/api/uploads/" + UUID.randomUUID() + "/x.gif")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[] {1}))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_rejectsWhenContentTooLarge() throws Exception {
        UUID boardId = UUID.randomUUID();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("image/png");
        when(request.getContentLengthLong()).thenReturn(ImageUploadConstraints.MAX_BYTES + 1);
        when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(new byte[0])));

        ResponseEntity<?> res = controller.put(boardId.toString(), "x.png", request);

        assertThat(res.getStatusCode().value()).isEqualTo(413);
    }
}
