package org.bruneel.thankyouboard.images;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bruneel.thankyouboard.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImageUploadController.class)
@Import(SecurityConfig.class)
class ImageUploadControllerTest {

    private static final String ACCEPT_VERSION_1 = "application/json;version=1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImagePresignService presignService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void presign_returnsPresignedUpload() throws Exception {
        UUID boardId = UUID.randomUUID();
        PresignedUpload presigned = new PresignedUpload(
                "/api/uploads/" + boardId + "/x.png",
                "http://localhost:8080/uploads/" + boardId + "/x.png",
                600,
                "image/png"
        );
        given(presignService.presign(eq(boardId), eq("image/png"), eq(1024L))).willReturn(presigned);

        mockMvc.perform(post("/api/boards/" + boardId + "/images/presign")
                        .accept(ACCEPT_VERSION_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentType", "image/png",
                                "contentLengthBytes", 1024
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value(presigned.uploadUrl()))
                .andExpect(jsonPath("$.imageUrl").value(presigned.imageUrl()))
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void presign_returns400WhenValidationFails() throws Exception {
        UUID boardId = UUID.randomUUID();
        given(presignService.presign(any(), any(), anyLong()))
                .willThrow(new IllegalArgumentException("bad request"));

        mockMvc.perform(post("/api/boards/" + boardId + "/images/presign")
                        .accept(ACCEPT_VERSION_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad request"));
    }
}
