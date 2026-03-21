package org.bruneel.thankyouboard.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bruneel.thankyouboard.domain.Board;
import org.bruneel.thankyouboard.domain.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.bruneel.thankyouboard.security.SecurityConfig;
import org.bruneel.thankyouboard.service.CapabilityTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, CapabilityTokenService.class})
@org.springframework.test.context.TestPropertySource(properties = {
        "boards.uploaded-images.allowed-hosts=localhost,127.0.0.1",
        "boards.max-posts-per-board=2"
})
class PostControllerTest {

    private static final String ACCEPT_VERSION_1 = "application/json;version=1";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private CapabilityTokenService capabilityTokenService;

    @MockitoBean
    private PostRepository postRepository;

    @MockitoBean
    private BoardRepository boardRepository;

    @Test
    void getPostsByBoardId_returnsEmptyList() throws Exception {
        UUID boardId = UUID.randomUUID();
        given(postRepository.findByBoardIdOrderByCreatedAtAsc(boardId)).willReturn(List.of());

        mockMvc.perform(get("/api/boards/" + boardId + "/posts").accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getPostsByBoardId_returnsPostsOrderedByCreatedAt() throws Exception {
        UUID boardId = UUID.randomUUID();
        Post post = createPost(UUID.randomUUID(), boardId, "Alice", "Hello!", null);
        given(postRepository.findByBoardIdOrderByCreatedAtAsc(boardId)).willReturn(List.of(post));

        mockMvc.perform(get("/api/boards/" + boardId + "/posts").accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].authorName").value("Alice"))
                .andExpect(jsonPath("$[0].messageText").value("Hello!"));
    }

    @Test
    void createPost_returnsSavedPost() throws Exception {
        UUID boardId = UUID.randomUUID();
        given(postRepository.countByBoardId(boardId)).willReturn(0L);
        var body = Map.of(
                "authorName", "Bob",
                "messageText", "Happy birthday!",
                "giphyUrl", "https://giphy.com/example"
        );

        Post saved = createPost(UUID.randomUUID(), boardId, "Bob", "Happy birthday!", "https://giphy.com/example");
        given(postRepository.save(any(Post.class))).willReturn(saved);

        mockMvc.perform(post("/api/boards/" + boardId + "/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorName").value("Bob"))
                .andExpect(jsonPath("$.messageText").value("Happy birthday!"))
                .andExpect(jsonPath("$.giphyUrl").value("https://giphy.com/example"))
                .andExpect(jsonPath("$.editDeleteToken").value(not(emptyString())));
    }

    @Test
    void createPost_returnsBadRequestWhenBothGiphyAndUploadProvided() throws Exception {
        UUID boardId = UUID.randomUUID();
        given(postRepository.countByBoardId(boardId)).willReturn(0L);
        var body = Map.of(
                "authorName", "Bob",
                "messageText", "Happy birthday!",
                "giphyUrl", "https://giphy.com/example",
                "uploadedImageUrl", "http://localhost:8080/uploads/example.png"
        );

        mockMvc.perform(post("/api/boards/" + boardId + "/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("At most one of giphyUrl or uploadedImageUrl may be set"));

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void createPost_returnsBadRequestWhenAuthorMissing() throws Exception {
        UUID boardId = UUID.randomUUID();
        given(postRepository.countByBoardId(boardId)).willReturn(0L);
        var body = Map.of(
                "messageText", "Hello"
        );

        mockMvc.perform(post("/api/boards/" + boardId + "/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("authorName required"));
    }

    @Test
    void createPost_returnsBadRequestWhenUploadedImageHostNotAllowed() throws Exception {
        UUID boardId = UUID.randomUUID();
        var body = Map.of(
                "authorName", "Bob",
                "messageText", "Hello",
                "uploadedImageUrl", "https://example.com/bad.png"
        );

        mockMvc.perform(post("/api/boards/" + boardId + "/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("uploadedImageUrl must be an http(s) URL from an allowed host"));

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void createPost_returnsConflictWhenBoardPostLimitReached() throws Exception {
        UUID boardId = UUID.randomUUID();
        given(postRepository.countByBoardId(boardId)).willReturn(2L);

        var body = Map.of(
                "authorName", "Bob",
                "messageText", "One more post",
                "giphyUrl", "https://giphy.com/example"
        );

        mockMvc.perform(post("/api/boards/" + boardId + "/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Board post limit reached (2)."));

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void createPost_returnsBadRequestWhenGiphyHostNotAllowed() throws Exception {
        UUID boardId = UUID.randomUUID();
        var body = Map.of(
                "authorName", "Bob",
                "messageText", "Hello",
                "giphyUrl", "https://example.com/bad.gif"
        );

        mockMvc.perform(post("/api/boards/" + boardId + "/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("giphyUrl must be an https URL from an allowed host"));

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void updatePost_requiresAuthentication() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        var body = Map.of("messageText", "Updated");

        mockMvc.perform(put("/api/boards/" + boardId + "/posts/" + postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePost_returnsForbiddenWhenNotBoardOwner() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        var body = Map.of("messageText", "Updated");

        Board board = new Board();
        board.setId(boardId);
        board.setOwnerId("someone-else");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        Post existing = createPost(postId, boardId, "Alice", "Hello", null);
        given(postRepository.findById(postId)).willReturn(Optional.of(existing));

        mockMvc.perform(put("/api/boards/" + boardId + "/posts/" + postId)
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePost_updatesAndReturnsPostWhenOwner() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        var body = Map.of(
                "authorName", "Bob",
                "messageText", "Updated message",
                "giphyUrl", "https://giphy.com/example"
        );

        Board board = new Board();
        board.setId(boardId);
        board.setOwnerId("test|user");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        Post existing = createPost(postId, boardId, "Alice", "Hello", null);
        given(postRepository.findById(postId)).willReturn(Optional.of(existing));

        Post saved = createPost(postId, boardId, "Bob", "Updated message", "https://giphy.com/example");
        given(postRepository.save(any(Post.class))).willReturn(saved);

        mockMvc.perform(put("/api/boards/" + boardId + "/posts/" + postId)
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorName").value("Bob"))
                .andExpect(jsonPath("$.messageText").value("Updated message"))
                .andExpect(jsonPath("$.giphyUrl").value("https://giphy.com/example"));
    }

    @Test
    void updatePost_updatesAndReturnsPostWhenAnonymousCapabilityTokenValid() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        var issued = capabilityTokenService.issueToken();

        var body = Map.of(
                "authorName", "Bob",
                "messageText", "Updated message",
                "giphyUrl", "https://giphy.com/example"
        );

        Board board = new Board();
        board.setId(boardId);
        board.setOwnerId("someone-else");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        Post existing = createPost(postId, boardId, "Alice", "Hello", null);
        existing.setCapabilityTokenHash(issued.tokenHashHex());
        existing.setCapabilityTokenExpiresAt(issued.expiresAt());
        given(postRepository.findById(postId)).willReturn(Optional.of(existing));

        Post saved = createPost(postId, boardId, "Bob", "Updated message", "https://giphy.com/example");
        given(postRepository.save(any(Post.class))).willReturn(saved);

        mockMvc.perform(put("/api/boards/" + boardId + "/posts/" + postId)
                        .header(PostController.POST_EDIT_DELETE_CAPABILITY_HEADER, issued.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorName").value("Bob"))
                .andExpect(jsonPath("$.messageText").value("Updated message"));
    }

    @Test
    void updatePost_returnsForbiddenWhenAnonymousCapabilityTokenInvalid() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        var issued = capabilityTokenService.issueToken();

        var body = Map.of("messageText", "Updated");

        Board board = new Board();
        board.setId(boardId);
        board.setOwnerId("someone-else");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        Post existing = createPost(postId, boardId, "Alice", "Hello", null);
        existing.setCapabilityTokenHash(issued.tokenHashHex());
        existing.setCapabilityTokenExpiresAt(issued.expiresAt());
        given(postRepository.findById(postId)).willReturn(Optional.of(existing));

        mockMvc.perform(put("/api/boards/" + boardId + "/posts/" + postId)
                        .header(PostController.POST_EDIT_DELETE_CAPABILITY_HEADER, "definitely-wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void updatePost_returnsForbiddenWhenAnonymousCapabilityTokenExpired() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        var issued = capabilityTokenService.issueToken();

        var body = Map.of("messageText", "Updated");

        Board board = new Board();
        board.setId(boardId);
        board.setOwnerId("someone-else");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        Post existing = createPost(postId, boardId, "Alice", "Hello", null);
        existing.setCapabilityTokenHash(issued.tokenHashHex());
        existing.setCapabilityTokenExpiresAt(ZonedDateTime.now(ZoneOffset.UTC).minusHours(1));
        given(postRepository.findById(postId)).willReturn(Optional.of(existing));

        mockMvc.perform(put("/api/boards/" + boardId + "/posts/" + postId)
                        .header(PostController.POST_EDIT_DELETE_CAPABILITY_HEADER, issued.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void deletePost_requiresAuthentication() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        mockMvc.perform(delete("/api/boards/" + boardId + "/posts/" + postId)
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletePost_deletesWhenOwner() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        Board board = new Board();
        board.setId(boardId);
        board.setOwnerId("test|user");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        Post existing = createPost(postId, boardId, "Alice", "Hello", null);
        given(postRepository.findById(postId)).willReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/boards/" + boardId + "/posts/" + postId)
                        .header("Authorization", "Bearer test")
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isNoContent());

        verify(postRepository).delete(existing);
    }

    @Test
    void deletePost_deletesWhenAnonymousCapabilityTokenValid() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        var issued = capabilityTokenService.issueToken();

        Board board = new Board();
        board.setId(boardId);
        board.setOwnerId("someone-else");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        Post existing = createPost(postId, boardId, "Alice", "Hello", null);
        existing.setCapabilityTokenHash(issued.tokenHashHex());
        existing.setCapabilityTokenExpiresAt(issued.expiresAt());
        given(postRepository.findById(postId)).willReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/boards/" + boardId + "/posts/" + postId)
                        .header(PostController.POST_EDIT_DELETE_CAPABILITY_HEADER, issued.token())
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isNoContent());

        verify(postRepository).delete(existing);
    }

    @Test
    void deletePost_returnsForbiddenWhenAnonymousCapabilityTokenInvalid() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        var issued = capabilityTokenService.issueToken();

        Board board = new Board();
        board.setId(boardId);
        board.setOwnerId("someone-else");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        Post existing = createPost(postId, boardId, "Alice", "Hello", null);
        existing.setCapabilityTokenHash(issued.tokenHashHex());
        existing.setCapabilityTokenExpiresAt(issued.expiresAt());
        given(postRepository.findById(postId)).willReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/boards/" + boardId + "/posts/" + postId)
                        .header(PostController.POST_EDIT_DELETE_CAPABILITY_HEADER, "definitely-wrong-token")
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isForbidden());

        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void deletePost_returnsForbiddenWhenAnonymousCapabilityTokenExpired() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        var issued = capabilityTokenService.issueToken();

        Board board = new Board();
        board.setId(boardId);
        board.setOwnerId("someone-else");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        Post existing = createPost(postId, boardId, "Alice", "Hello", null);
        existing.setCapabilityTokenHash(issued.tokenHashHex());
        existing.setCapabilityTokenExpiresAt(ZonedDateTime.now(ZoneOffset.UTC).minusHours(1));
        given(postRepository.findById(postId)).willReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/boards/" + boardId + "/posts/" + postId)
                        .header(PostController.POST_EDIT_DELETE_CAPABILITY_HEADER, issued.token())
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isForbidden());

        verify(postRepository, never()).delete(any(Post.class));
    }

    @Test
    void updatePost_returnsNotFoundWhenBoardMissing() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        var body = Map.of("messageText", "Updated");

        given(boardRepository.findById(boardId)).willReturn(Optional.empty());

        mockMvc.perform(put("/api/boards/" + boardId + "/posts/" + postId)
                        .header("Authorization", "Bearer test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(ACCEPT_VERSION_1)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePost_returnsNotFoundWhenBoardMissing() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        given(boardRepository.findById(boardId)).willReturn(Optional.empty());

        mockMvc.perform(delete("/api/boards/" + boardId + "/posts/" + postId)
                        .header("Authorization", "Bearer test")
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isNotFound());
    }

    private static Post createPost(UUID id, UUID boardId, String authorName, String messageText, String giphyUrl) {
        Post post = new Post();
        post.setId(id);
        post.setBoardId(boardId);
        post.setAuthorName(authorName);
        post.setMessageText(messageText);
        post.setGiphyUrl(giphyUrl);
        post.setUploadedImageUrl(null);
        post.setCreatedAt(ZonedDateTime.now());
        return post;
    }
}
