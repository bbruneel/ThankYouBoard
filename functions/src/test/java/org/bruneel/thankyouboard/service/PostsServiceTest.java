package org.bruneel.thankyouboard.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.bruneel.thankyouboard.model.Board;
import org.bruneel.thankyouboard.model.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PostsServiceTest {

    @Test
    void createPost_returns404WhenBoardNotFound() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 500, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "");

        UUID boardId = UUID.randomUUID();
        when(boardRepository.findById(boardId)).thenReturn(Optional.empty());

        APIGatewayProxyResponseEvent res = service.createPost(boardId.toString(), "{\"authorName\":\"Alex\"}");

        assertThat(res.getStatusCode()).isEqualTo(404);
        verify(boardRepository).findById(boardId);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void createPost_returns409WhenBoardPostLimitReached() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "");

        UUID boardId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(postRepository.countByBoardId(boardId)).thenReturn(3L);

        APIGatewayProxyResponseEvent res = service.createPost(
                boardId.toString(),
                "{\"authorName\":\"Alex\",\"messageText\":\"Hello\"}"
        );

        assertThat(res.getStatusCode()).isEqualTo(409);
        assertThat(res.getBody()).contains("Board post limit reached (3).");
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void createPost_savesWhenBelowBoardPostLimit() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "");

        UUID boardId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(postRepository.countByBoardId(boardId)).thenReturn(2L);

        APIGatewayProxyResponseEvent res = service.createPost(
                boardId.toString(),
                "{\"authorName\":\"Alex\",\"messageText\":\"Hello\"}"
        );

        assertThat(res.getStatusCode()).isEqualTo(200);
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getBoardId()).isEqualTo(boardId);
        assertThat(captor.getValue().getAuthorName()).isEqualTo("Alex");
    }

    @Test
    void createPost_rejectsDisallowedGiphyHost() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "");

        UUID boardId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));

        APIGatewayProxyResponseEvent res = service.createPost(
                boardId.toString(),
                "{\"authorName\":\"Alex\",\"messageText\":\"Hello\",\"giphyUrl\":\"https://example.com/bad.gif\"}"
        );

        assertThat(res.getStatusCode()).isEqualTo(400);
        assertThat(res.getBody()).contains("giphyUrl must be an https URL from an allowed host");
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void createPost_rejectsWhenBothGiphyAndUploadProvided() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "localhost");

        UUID boardId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));

        APIGatewayProxyResponseEvent res = service.createPost(
                boardId.toString(),
                "{\"authorName\":\"Alex\",\"messageText\":\"Hello\",\"giphyUrl\":\"https://giphy.com/example\",\"uploadedImageUrl\":\"http://localhost:8080/uploads/a.png\"}"
        );

        assertThat(res.getStatusCode()).isEqualTo(400);
        assertThat(res.getBody()).contains("At most one of giphyUrl or uploadedImageUrl may be set");
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void updatePost_returns401WhenMissingOwnerId() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "localhost");

        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        APIGatewayProxyResponseEvent res = service.updatePost(boardId.toString(), postId.toString(), "{\"messageText\":\"Hi\"}", null);
        assertThat(res.getStatusCode()).isEqualTo(401);
    }

    @Test
    void updatePost_returns403WhenNotBoardOwner() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "localhost");

        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(postRepository.findById(postId)).thenReturn(Optional.of(new Post(postId, boardId, "Alice", "Hello", null, null, ZonedDateTime.now())));

        APIGatewayProxyResponseEvent res = service.updatePost(boardId.toString(), postId.toString(), "{\"messageText\":\"Hi\"}", "auth0|other");
        assertThat(res.getStatusCode()).isEqualTo(403);
    }

    @Test
    void updatePost_returns404WhenPostNotFound() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "localhost");

        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        APIGatewayProxyResponseEvent res = service.updatePost(boardId.toString(), postId.toString(), "{\"messageText\":\"Hi\"}", "auth0|owner");
        assertThat(res.getStatusCode()).isEqualTo(404);
    }

    @Test
    void updatePost_happyPathCallsRepositoryUpdate() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "localhost");

        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));

        Post existing = new Post(postId, boardId, "Alice", "Hello", null, null, ZonedDateTime.now());
        when(postRepository.findById(postId)).thenReturn(Optional.of(existing));

        Post updated = new Post(postId, boardId, "Bob", "Updated", "https://giphy.com/example", null, existing.getCreatedAt());
        when(postRepository.updatePost(eq(existing), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(updated);

        APIGatewayProxyResponseEvent res = service.updatePost(
                boardId.toString(),
                postId.toString(),
                "{\"authorName\":\"Bob\",\"messageText\":\"Updated\",\"giphyUrl\":\"https://giphy.com/example\"}",
                "auth0|owner"
        );

        assertThat(res.getStatusCode()).isEqualTo(200);
        verify(postRepository).updatePost(eq(existing), eq(true), eq("Bob"), eq(true), eq("Updated"), eq(true), eq("https://giphy.com/example"), eq(false), isNull());
    }

    @Test
    void deletePost_returns401WhenMissingOwnerId() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "localhost");

        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        APIGatewayProxyResponseEvent res = service.deletePost(boardId.toString(), postId.toString(), "");
        assertThat(res.getStatusCode()).isEqualTo(401);
    }

    @Test
    void deletePost_happyPathCallsRepositoryDelete() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "localhost");

        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));

        Post existing = new Post(postId, boardId, "Alice", "Hello", null, null, ZonedDateTime.now());
        when(postRepository.findById(postId)).thenReturn(Optional.of(existing));

        APIGatewayProxyResponseEvent res = service.deletePost(boardId.toString(), postId.toString(), "auth0|owner");
        assertThat(res.getStatusCode()).isEqualTo(204);
        verify(postRepository).delete(existing);
    }

    @Test
    void updatePost_allowsAnonymousCapabilityTokenValid() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "localhost");

        CapabilityTokenService tokenService = new CapabilityTokenService(32, 24);
        CapabilityTokenService.IssuedToken issued = tokenService.issueToken();

        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));

        Post existing = new Post(postId, boardId, "Alice", "Hello", null, null, ZonedDateTime.now());
        existing.setCapabilityTokenHash(issued.tokenHashHex());
        existing.setCapabilityTokenExpiresAt(issued.expiresAt());
        when(postRepository.findById(postId)).thenReturn(Optional.of(existing));

        Post updated = new Post(postId, boardId, "Alice", "Hi", null, null, existing.getCreatedAt());
        when(postRepository.updatePost(eq(existing), eq(false), isNull(), eq(true), eq("Hi"), eq(false), isNull(), eq(false), isNull()))
                .thenReturn(updated);

        APIGatewayProxyResponseEvent res = service.updatePost(
                boardId.toString(),
                postId.toString(),
                "{\"messageText\":\"Hi\"}",
                null,
                issued.token()
        );

        assertThat(res.getStatusCode()).isEqualTo(200);
        verify(postRepository).updatePost(eq(existing), eq(false), isNull(), eq(true), eq("Hi"), eq(false), isNull(), eq(false), isNull());
    }

    @Test
    void updatePost_rejectsWhenAnonymousCapabilityTokenInvalid() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "localhost");

        CapabilityTokenService tokenService = new CapabilityTokenService(32, 24);
        CapabilityTokenService.IssuedToken issued = tokenService.issueToken();

        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));

        Post existing = new Post(postId, boardId, "Alice", "Hello", null, null, ZonedDateTime.now());
        existing.setCapabilityTokenHash(issued.tokenHashHex());
        existing.setCapabilityTokenExpiresAt(issued.expiresAt());
        when(postRepository.findById(postId)).thenReturn(Optional.of(existing));

        APIGatewayProxyResponseEvent res = service.updatePost(
                boardId.toString(),
                postId.toString(),
                "{\"messageText\":\"Hi\"}",
                null,
                "definitely-wrong-token"
        );

        assertThat(res.getStatusCode()).isEqualTo(403);
        verify(postRepository, never()).updatePost(any(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any());
    }

    @Test
    void updatePost_rejectsWhenAnonymousCapabilityTokenExpired() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "localhost");

        CapabilityTokenService tokenService = new CapabilityTokenService(32, 24);
        CapabilityTokenService.IssuedToken issued = tokenService.issueToken();

        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));

        Post existing = new Post(postId, boardId, "Alice", "Hello", null, null, ZonedDateTime.now());
        existing.setCapabilityTokenHash(issued.tokenHashHex());
        existing.setCapabilityTokenExpiresAt(ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        when(postRepository.findById(postId)).thenReturn(Optional.of(existing));

        APIGatewayProxyResponseEvent res = service.updatePost(
                boardId.toString(),
                postId.toString(),
                "{\"messageText\":\"Hi\"}",
                null,
                issued.token()
        );

        assertThat(res.getStatusCode()).isEqualTo(403);
        verify(postRepository, never()).updatePost(any(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), any());
    }

    @Test
    void deletePost_allowsAnonymousCapabilityTokenValid() {
        PostRepository postRepository = mock(PostRepository.class);
        BoardRepository boardRepository = mock(BoardRepository.class);
        PostsService service = new PostsService(postRepository, boardRepository, 3, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "localhost");

        CapabilityTokenService tokenService = new CapabilityTokenService(32, 24);
        CapabilityTokenService.IssuedToken issued = tokenService.issueToken();

        UUID boardId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Board board = new Board(boardId, "auth0|owner", "Board", "Recipient", ZonedDateTime.now());
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));

        Post existing = new Post(postId, boardId, "Alice", "Hello", null, null, ZonedDateTime.now());
        existing.setCapabilityTokenHash(issued.tokenHashHex());
        existing.setCapabilityTokenExpiresAt(issued.expiresAt());
        when(postRepository.findById(postId)).thenReturn(Optional.of(existing));

        APIGatewayProxyResponseEvent res = service.deletePost(boardId.toString(), postId.toString(), null, issued.token());

        assertThat(res.getStatusCode()).isEqualTo(204);
        verify(postRepository).delete(existing);
    }
}
