package org.bruneel.thankyouboard.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.bruneel.thankyouboard.model.Board;
import org.bruneel.thankyouboard.model.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BoardsServiceTest {

    @Test
    void createBoard_requiresOwnerId() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        APIGatewayProxyResponseEvent res = service.createBoard("{\"title\":\"t\",\"recipientName\":\"r\"}", null);
        assertThat(res.getStatusCode()).isEqualTo(401);
        verifyNoInteractions(repo);
    }

    @Test
    void createBoard_setsOwnerIdAndSaves() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        String ownerId = "auth0|user-1";
        when(repo.countByOwnerId(ownerId)).thenReturn(0L);
        APIGatewayProxyResponseEvent res = service.createBoard("{\"title\":\"My Board\",\"recipientName\":\"Alice\"}", ownerId);

        assertThat(res.getStatusCode()).isEqualTo(200);

        ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
        verify(repo).countByOwnerId(ownerId);
        verify(repo).save(captor.capture());
        Board saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOwnerId()).isEqualTo(ownerId);
        assertThat(saved.getTitle()).isEqualTo("My Board");
        assertThat(saved.getRecipientName()).isEqualTo("Alice");
        assertThat(saved.getCreatedAt()).isNotNull();
        verifyNoMoreInteractions(repo);
    }

    @Test
    void createBoard_rejectsWhenBoardLimitReached() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo, 2);

        String ownerId = "auth0|user-1";
        when(repo.countByOwnerId(ownerId)).thenReturn(2L);

        APIGatewayProxyResponseEvent res = service.createBoard(
                "{\"title\":\"My Board\",\"recipientName\":\"Alice\"}",
                ownerId
        );

        assertThat(res.getStatusCode()).isEqualTo(409);
        assertThat(res.getBody()).contains("Board limit reached (2).");
        verify(repo).countByOwnerId(ownerId);
        verify(repo, never()).save(any(Board.class));
        verifyNoMoreInteractions(repo);
    }

    @Test
    void listBoards_requiresOwnerId() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        APIGatewayProxyResponseEvent res = service.listBoards("  ");
        assertThat(res.getStatusCode()).isEqualTo(401);
        verifyNoInteractions(repo);
    }

    @Test
    void listBoards_scopesByOwnerId() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        String ownerId = "auth0|user-1";
        when(repo.findByOwnerId(ownerId)).thenReturn(List.of(
                new Board(UUID.randomUUID(), ownerId, "t1", "r1", java.time.ZonedDateTime.now())
        ));

        APIGatewayProxyResponseEvent res = service.listBoards(ownerId);
        assertThat(res.getStatusCode()).isEqualTo(200);
        verify(repo).findByOwnerId(ownerId);
        verifyNoMoreInteractions(repo);
    }

    @Test
    void updateBoard_requiresOwnerId() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        APIGatewayProxyResponseEvent res = service.updateBoard("abc", "{\"title\":\"t\",\"recipientName\":\"r\"}", null);
        assertThat(res.getStatusCode()).isEqualTo(401);
        verifyNoInteractions(repo);
    }

    @Test
    void updateBoard_returns404WhenNotFound() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        when(repo.findById(any())).thenReturn(java.util.Optional.empty());

        APIGatewayProxyResponseEvent res = service.updateBoard(UUID.randomUUID().toString(), "{\"title\":\"t\",\"recipientName\":\"r\"}", "auth0|user-1");
        assertThat(res.getStatusCode()).isEqualTo(404);
        verify(repo).findById(any());
        verifyNoMoreInteractions(repo);
    }

    @Test
    void updateBoard_returns403WhenNotOwner() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        UUID id = UUID.randomUUID();
        Board existing = new Board(id, "auth0|other", "t", "r", java.time.ZonedDateTime.now());
        when(repo.findById(id)).thenReturn(java.util.Optional.of(existing));

        APIGatewayProxyResponseEvent res = service.updateBoard(id.toString(), "{\"title\":\"t\",\"recipientName\":\"r\"}", "auth0|user-1");
        assertThat(res.getStatusCode()).isEqualTo(403);
        verify(repo).findById(id);
        verifyNoMoreInteractions(repo);
    }

    @Test
    void updateBoard_updatesWhenOwner() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        UUID id = UUID.randomUUID();
        String ownerId = "auth0|user-1";
        Board existing = new Board(id, ownerId, "old", "old", java.time.ZonedDateTime.now());
        Board updated = new Board(id, ownerId, "new", "new", java.time.ZonedDateTime.now());

        when(repo.findById(id)).thenReturn(java.util.Optional.of(existing));
        when(repo.updateBoard(id, ownerId, "new", "new")).thenReturn(updated);

        APIGatewayProxyResponseEvent res = service.updateBoard(id.toString(), "{\"title\":\"new\",\"recipientName\":\"new\"}", ownerId);
        assertThat(res.getStatusCode()).isEqualTo(200);
        verify(repo).findById(id);
        verify(repo).updateBoard(id, ownerId, "new", "new");
        verifyNoMoreInteractions(repo);
    }

    @Test
    void deleteBoard_requiresOwnerId() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        APIGatewayProxyResponseEvent res = service.deleteBoard(UUID.randomUUID().toString(), null);
        assertThat(res.getStatusCode()).isEqualTo(401);
        verifyNoInteractions(repo);
    }

    @Test
    void deleteBoard_returns404WhenNotFound() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        when(repo.findById(any())).thenReturn(java.util.Optional.empty());

        APIGatewayProxyResponseEvent res = service.deleteBoard(UUID.randomUUID().toString(), "auth0|user-1");
        assertThat(res.getStatusCode()).isEqualTo(404);
        verify(repo).findById(any());
        verifyNoMoreInteractions(repo);
    }

    @Test
    void deleteBoard_returns403WhenNotOwner() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        UUID id = UUID.randomUUID();
        Board existing = new Board(id, "auth0|other", "t", "r", java.time.ZonedDateTime.now());
        when(repo.findById(id)).thenReturn(java.util.Optional.of(existing));

        APIGatewayProxyResponseEvent res = service.deleteBoard(id.toString(), "auth0|user-1");
        assertThat(res.getStatusCode()).isEqualTo(403);
        verify(repo).findById(id);
        verifyNoMoreInteractions(repo);
    }

    @Test
    void deleteBoard_deletesWhenOwner() {
        BoardRepository repo = mock(BoardRepository.class);
        BoardsService service = new BoardsService(repo);

        UUID id = UUID.randomUUID();
        String ownerId = "auth0|user-1";
        Board existing = new Board(id, ownerId, "t", "r", java.time.ZonedDateTime.now());
        when(repo.findById(id)).thenReturn(java.util.Optional.of(existing));

        APIGatewayProxyResponseEvent res = service.deleteBoard(id.toString(), ownerId);
        assertThat(res.getStatusCode()).isEqualTo(204);
        verify(repo).findById(id);
        verify(repo).deleteBoard(id, ownerId);
        verifyNoMoreInteractions(repo);
    }

    @Test
    void downloadBoardPdf_requiresOwnerId() {
        BoardRepository boardRepo = mock(BoardRepository.class);
        PostRepository postRepo = mock(PostRepository.class);
        BoardsService service = new BoardsService(boardRepo, postRepo);

        APIGatewayProxyResponseEvent res = service.downloadBoardPdf(UUID.randomUUID().toString(), null);

        assertThat(res.getStatusCode()).isEqualTo(401);
        verifyNoInteractions(boardRepo, postRepo);
    }

    @Test
    void downloadBoardPdf_returns404WhenBoardNotFound() {
        BoardRepository boardRepo = mock(BoardRepository.class);
        PostRepository postRepo = mock(PostRepository.class);
        BoardsService service = new BoardsService(boardRepo, postRepo);

        UUID id = UUID.randomUUID();
        when(boardRepo.findById(id)).thenReturn(java.util.Optional.empty());

        APIGatewayProxyResponseEvent res = service.downloadBoardPdf(id.toString(), "auth0|user-1");

        assertThat(res.getStatusCode()).isEqualTo(404);
        verify(boardRepo).findById(id);
        verifyNoInteractions(postRepo);
    }

    @Test
    void downloadBoardPdf_returns403WhenNotOwner() {
        BoardRepository boardRepo = mock(BoardRepository.class);
        PostRepository postRepo = mock(PostRepository.class);
        BoardsService service = new BoardsService(boardRepo, postRepo);

        UUID id = UUID.randomUUID();
        Board existing = new Board(id, "auth0|owner", "t", "r", java.time.ZonedDateTime.now());
        when(boardRepo.findById(id)).thenReturn(java.util.Optional.of(existing));

        APIGatewayProxyResponseEvent res = service.downloadBoardPdf(id.toString(), "auth0|other");

        assertThat(res.getStatusCode()).isEqualTo(403);
        verify(boardRepo).findById(id);
        verifyNoInteractions(postRepo);
    }

    @Test
    void downloadBoardPdf_returnsBase64EncodedPdfForOwner() {
        BoardRepository boardRepo = mock(BoardRepository.class);
        PostRepository postRepo = mock(PostRepository.class);
        BoardsService service = new BoardsService(boardRepo, postRepo);

        UUID id = UUID.randomUUID();
        String ownerId = "auth0|owner";
        Board board = new Board(id, ownerId, "Farewell 😊", "Álex", java.time.ZonedDateTime.now());
        Post post = new Post(UUID.randomUUID(), id, "Bram", "<p>ありがとう!</p>", tinyPngDataUri(), null, java.time.ZonedDateTime.now());
        when(boardRepo.findById(id)).thenReturn(java.util.Optional.of(board));
        when(postRepo.findByBoardIdOrderByCreatedAtAsc(id)).thenReturn(List.of(post));

        APIGatewayProxyResponseEvent res = service.downloadBoardPdf(id.toString(), ownerId);

        assertThat(res.getStatusCode()).isEqualTo(200);
        assertThat(res.getHeaders()).containsEntry("Content-Type", "application/pdf");
        assertThat(res.getHeaders().get("Content-Disposition")).contains(".pdf");
        assertThat(res.getIsBase64Encoded()).isTrue();
        byte[] bytes = java.util.Base64.getDecoder().decode(res.getBody());
        assertThat(new String(bytes, java.nio.charset.StandardCharsets.US_ASCII)).startsWith("%PDF-");
        assertThat(hasImage(bytes)).isTrue();
        verify(boardRepo).findById(id);
        verify(postRepo).findByBoardIdOrderByCreatedAtAsc(id);
    }

    private static boolean hasImage(byte[] bytes) {
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
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String tinyPngDataUri() {
        return "data:image/gif;base64,"
                + "R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==";
    }
}

