package org.bruneel.thankyouboard.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bruneel.thankyouboard.domain.Board;
import org.bruneel.thankyouboard.domain.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.bruneel.thankyouboard.security.SecurityConfig;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BoardController.class)
@Import(SecurityConfig.class)
@org.springframework.test.context.TestPropertySource(properties = {
        "boards.max-boards-per-owner=2"
})
class BoardControllerTest {

    private static final String ACCEPT_VERSION_1 = "application/json;version=1";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private BoardRepository boardRepository;

    @MockitoBean
    private PostRepository postRepository;

    @Test
    void getAllBoards_returnsEmptyList() throws Exception {
        String sub = "auth0|user-1";
        given(boardRepository.findByOwnerIdOrderByCreatedAtAsc(sub)).willReturn(List.of());

        mockMvc.perform(get("/api/boards").accept(ACCEPT_VERSION_1))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/boards")
                        .with(jwt().jwt(j -> j.subject(sub)))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getAllBoards_returnsBoards() throws Exception {
        String sub = "auth0|user-1";
        Board board = createBoard(UUID.randomUUID(), sub, "My Board", "Alice");
        given(boardRepository.findByOwnerIdOrderByCreatedAtAsc(sub)).willReturn(List.of(board));

        mockMvc.perform(get("/api/boards")
                        .with(jwt().jwt(j -> j.subject(sub)))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("My Board"))
                .andExpect(jsonPath("$[0].recipientName").value("Alice"));
    }

    @Test
    void getBoardById_returns404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        given(boardRepository.findById(id)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/boards/" + id).accept(ACCEPT_VERSION_1))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBoardById_returnsBoardWhenFound() throws Exception {
        UUID id = UUID.randomUUID();
        Board board = createBoard(id, "auth0|user-1", "Birthday Board", "Bob");
        given(boardRepository.findById(id)).willReturn(Optional.of(board));

        mockMvc.perform(get("/api/boards/" + id).accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Birthday Board"))
                .andExpect(jsonPath("$.recipientName").value("Bob"))
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void getBoardById_setsCanEditTrueForOwner() throws Exception {
        UUID id = UUID.randomUUID();
        String ownerSub = "auth0|owner";
        Board board = createBoard(id, ownerSub, "Owner Board", "Dana");
        given(boardRepository.findById(id)).willReturn(Optional.of(board));

        mockMvc.perform(get("/api/boards/" + id)
                        .with(jwt().jwt(j -> j.subject(ownerSub)))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    void getBoardById_setsCanEditFalseForDifferentAuthenticatedUser() throws Exception {
        UUID id = UUID.randomUUID();
        Board board = createBoard(id, "auth0|owner", "Owner Board", "Dana");
        given(boardRepository.findById(id)).willReturn(Optional.of(board));

        mockMvc.perform(get("/api/boards/" + id)
                        .with(jwt().jwt(j -> j.subject("auth0|other-user")))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void createBoard_returnsSavedBoard() throws Exception {
        String sub = "auth0|user-1";
        Board input = new Board();
        input.setTitle("New Board");
        input.setRecipientName("Charlie");

        Board saved = createBoard(UUID.randomUUID(), sub, "New Board", "Charlie");
        given(boardRepository.countByOwnerId(sub)).willReturn(0L);
        given(boardRepository.save(any(Board.class))).willReturn(saved);

        mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/boards")
                        .with(jwt().jwt(j -> j.subject(sub)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Board"))
                .andExpect(jsonPath("$.recipientName").value("Charlie"));

        ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
        verify(boardRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getOwnerId()).isEqualTo(sub);
    }

    @Test
    void createBoard_returnsConflictWhenBoardLimitReached() throws Exception {
        String sub = "auth0|user-1";
        Board input = new Board();
        input.setTitle("New Board");
        input.setRecipientName("Charlie");

        given(boardRepository.countByOwnerId(sub)).willReturn(2L);

        mockMvc.perform(post("/api/boards")
                        .with(jwt().jwt(j -> j.subject(sub)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Board limit reached (2)."));

        verify(boardRepository, never()).save(any(Board.class));
    }

    @Test
    void updateBoard_requiresAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        Board existing = createBoard(id, "auth0|owner", "Old", "Old Recipient");
        given(boardRepository.findById(id)).willReturn(Optional.of(existing));

        Board update = new Board();
        update.setTitle("Updated");

        mockMvc.perform(put("/api/boards/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteBoard_requiresAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        Board existing = createBoard(id, "auth0|owner", "Old", "Old Recipient");
        given(boardRepository.findById(id)).willReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/boards/" + id)
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateBoard_returns404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        given(boardRepository.findById(id)).willReturn(Optional.empty());

        Board update = new Board();
        update.setTitle("Updated");

        mockMvc.perform(put("/api/boards/" + id)
                        .with(jwt().jwt(j -> j.subject("auth0|owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateBoard_returns403WhenUserIsNotOwner() throws Exception {
        UUID id = UUID.randomUUID();
        Board existing = createBoard(id, "auth0|owner", "Old", "Old Recipient");
        given(boardRepository.findById(id)).willReturn(Optional.of(existing));

        Board update = new Board();
        update.setTitle("Updated");

        mockMvc.perform(put("/api/boards/" + id)
                        .with(jwt().jwt(j -> j.subject("auth0|other-user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateBoard_updatesEditableFieldsForOwner() throws Exception {
        UUID id = UUID.randomUUID();
        String ownerSub = "auth0|owner";
        Board existing = createBoard(id, ownerSub, "Old", "Old Recipient");
        given(boardRepository.findById(id)).willReturn(Optional.of(existing));

        Board updated = createBoard(id, ownerSub, "New Title", "New Recipient");
        given(boardRepository.save(any(Board.class))).willReturn(updated);

        Board updateRequest = new Board();
        updateRequest.setTitle("New Title");
        updateRequest.setRecipientName("New Recipient");

        mockMvc.perform(put("/api/boards/" + id)
                        .with(jwt().jwt(j -> j.subject(ownerSub)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.recipientName").value("New Recipient"));
    }

    @Test
    void deleteBoard_returns404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        given(boardRepository.findById(id)).willReturn(Optional.empty());

        mockMvc.perform(delete("/api/boards/" + id)
                        .with(jwt().jwt(j -> j.subject("auth0|owner")))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBoard_returns403WhenUserIsNotOwner() throws Exception {
        UUID id = UUID.randomUUID();
        Board existing = createBoard(id, "auth0|owner", "Old", "Old Recipient");
        given(boardRepository.findById(id)).willReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/boards/" + id)
                        .with(jwt().jwt(j -> j.subject("auth0|other-user")))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBoard_deletesForOwner() throws Exception {
        UUID id = UUID.randomUUID();
        String ownerSub = "auth0|owner";
        Board existing = createBoard(id, ownerSub, "Old", "Old Recipient");
        given(boardRepository.findById(id)).willReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/boards/" + id)
                        .with(jwt().jwt(j -> j.subject(ownerSub)))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isNoContent());
    }

    @Test
    void downloadBoardPdf_requiresAuthentication() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/boards/" + id + "/pdf")
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadBoardPdf_returns404WhenBoardNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        given(boardRepository.findById(id)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/boards/" + id + "/pdf")
                        .with(jwt().jwt(j -> j.subject("auth0|owner")))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadBoardPdf_returns403WhenUserIsNotOwner() throws Exception {
        UUID id = UUID.randomUUID();
        Board existing = createBoard(id, "auth0|owner", "Owner Board", "Dana");
        given(boardRepository.findById(id)).willReturn(Optional.of(existing));

        mockMvc.perform(get("/api/boards/" + id + "/pdf")
                        .with(jwt().jwt(j -> j.subject("auth0|other-user")))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadBoardPdf_returnsPdfForOwner() throws Exception {
        UUID id = UUID.randomUUID();
        String ownerSub = "auth0|owner";
        Board board = createBoard(id, ownerSub, "Farewell 😊 Board", "Álex");
        Post post = createPost(id, "Bram", "<p>ありがとう for everything!</p>", tinyPngDataUri());
        given(boardRepository.findById(id)).willReturn(Optional.of(board));
        given(postRepository.findByBoardIdOrderByCreatedAtAsc(id)).willReturn(List.of(post));

        mockMvc.perform(get("/api/boards/" + id + "/pdf")
                        .with(jwt().jwt(j -> j.subject(ownerSub)))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                .andExpect(result -> {
                    byte[] bytes = result.getResponse().getContentAsByteArray();
                    org.assertj.core.api.Assertions.assertThat(bytes).isNotEmpty();
                    org.assertj.core.api.Assertions.assertThat(new String(bytes, java.nio.charset.StandardCharsets.US_ASCII))
                            .startsWith("%PDF-");
                    org.assertj.core.api.Assertions.assertThat(hasImage(bytes)).isTrue();
                });
    }

    private static boolean hasImage(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                if (resources == null) {
                    continue;
                }
                for (var name : resources.getXObjectNames()) {
                    PDXObject xObject = resources.getXObject(name);
                    if (xObject instanceof PDImageXObject) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String tinyPngDataUri() {
        return "data:image/gif;base64,"
                + "R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==";
    }

    private static Board createBoard(UUID id, String ownerId, String title, String recipientName) {
        Board board = new Board();
        board.setId(id);
        board.setOwnerId(ownerId);
        board.setTitle(title);
        board.setRecipientName(recipientName);
        board.setCreatedAt(ZonedDateTime.now());
        return board;
    }

    private static Post createPost(UUID boardId, String authorName, String messageText, String giphyUrl) {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setBoardId(boardId);
        post.setAuthorName(authorName);
        post.setMessageText(messageText);
        post.setGiphyUrl(giphyUrl);
        post.setCreatedAt(ZonedDateTime.now());
        return post;
    }
}
