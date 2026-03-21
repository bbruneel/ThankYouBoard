package org.bruneel.thankyouboard.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bruneel.thankyouboard.model.Board;
import org.bruneel.thankyouboard.model.PdfJob;
import org.bruneel.thankyouboard.model.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PdfJobRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PdfJobServiceTest {

    private static final Gson GSON = new GsonBuilder().create();

    @Test
    void createPdfJob_returnsUnauthorizedWhenNoOwner() {
        PdfJobService service = createService();
        APIGatewayProxyResponseEvent response = service.createPdfJob(UUID.randomUUID().toString(), null);
        assertThat(response.getStatusCode()).isEqualTo(401);
    }

    @Test
    void createPdfJob_returns404WhenBoardNotFound() {
        BoardRepository boardRepo = mock(BoardRepository.class);
        when(boardRepo.findById(any())).thenReturn(Optional.empty());
        PdfJobService service = new PdfJobService(boardRepo, mock(PostRepository.class), mock(PdfJobRepository.class));

        APIGatewayProxyResponseEvent response = service.createPdfJob(UUID.randomUUID().toString(), "auth0|user");
        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void createPdfJob_returns403WhenNotOwner() {
        UUID boardId = UUID.randomUUID();
        BoardRepository boardRepo = mock(BoardRepository.class);
        when(boardRepo.findById(boardId)).thenReturn(Optional.of(
                new Board(boardId, "auth0|owner", "Title", "Rec", ZonedDateTime.now())));
        PdfJobService service = new PdfJobService(boardRepo, mock(PostRepository.class), mock(PdfJobRepository.class));

        APIGatewayProxyResponseEvent response = service.createPdfJob(boardId.toString(), "auth0|other");
        assertThat(response.getStatusCode()).isEqualTo(403);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createPdfJob_returns202AndCreatesJob() {
        UUID boardId = UUID.randomUUID();
        String ownerId = "auth0|owner";
        BoardRepository boardRepo = mock(BoardRepository.class);
        PdfJobRepository jobRepo = mock(PdfJobRepository.class);
        when(boardRepo.findById(boardId)).thenReturn(Optional.of(
                new Board(boardId, ownerId, "Title", "Rec", ZonedDateTime.now())));
        PdfJobService service = new PdfJobService(boardRepo, mock(PostRepository.class), jobRepo);

        APIGatewayProxyResponseEvent response = service.createPdfJob(boardId.toString(), ownerId);
        assertThat(response.getStatusCode()).isEqualTo(202);
        assertThat(response.getHeaders()).containsKey("Location");

        Map<String, Object> body = GSON.fromJson(response.getBody(), Map.class);
        assertThat(body.get("status")).isEqualTo("PENDING");
        assertThat(body.get("boardId")).isEqualTo(boardId.toString());
        assertThat(body.get("statusUrl")).isNotNull();

        verify(jobRepo).save(any(PdfJob.class));
    }

    @Test
    void getPdfJobStatus_returns404WhenJobNotFound() {
        PdfJobRepository jobRepo = mock(PdfJobRepository.class);
        when(jobRepo.findById(any())).thenReturn(Optional.empty());
        PdfJobService service = new PdfJobService(mock(BoardRepository.class), mock(PostRepository.class), jobRepo);

        UUID jobId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        APIGatewayProxyResponseEvent response = service.getPdfJobStatus(boardId.toString(), jobId.toString(), "auth0|user");
        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void getPdfJobStatus_returns403WhenNotOwner() {
        UUID jobId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        PdfJobRepository jobRepo = mock(PdfJobRepository.class);
        PdfJob job = new PdfJob(jobId, boardId, "auth0|owner", PdfJob.Status.PENDING,
                ZonedDateTime.now(), ZonedDateTime.now());
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        PdfJobService service = new PdfJobService(mock(BoardRepository.class), mock(PostRepository.class), jobRepo);

        APIGatewayProxyResponseEvent response = service.getPdfJobStatus(boardId.toString(), jobId.toString(), "auth0|other");
        assertThat(response.getStatusCode()).isEqualTo(403);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPdfJobStatus_returnsJobStatus() {
        UUID jobId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        String ownerId = "auth0|owner";
        PdfJobRepository jobRepo = mock(PdfJobRepository.class);
        PdfJob job = new PdfJob(jobId, boardId, ownerId, PdfJob.Status.RUNNING,
                ZonedDateTime.now(), ZonedDateTime.now());
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        PdfJobService service = new PdfJobService(mock(BoardRepository.class), mock(PostRepository.class), jobRepo);

        APIGatewayProxyResponseEvent response = service.getPdfJobStatus(boardId.toString(), jobId.toString(), ownerId);
        assertThat(response.getStatusCode()).isEqualTo(200);

        Map<String, Object> body = GSON.fromJson(response.getBody(), Map.class);
        assertThat(body.get("status")).isEqualTo("RUNNING");
        assertThat(body.get("jobId")).isEqualTo(jobId.toString());
    }

    @Test
    void processJob_updatesStatusToSucceeded() {
        UUID jobId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        String ownerId = "auth0|owner";

        PdfJobRepository jobRepo = mock(PdfJobRepository.class);
        BoardRepository boardRepo = mock(BoardRepository.class);
        PostRepository postRepo = mock(PostRepository.class);

        PdfJob job = new PdfJob(jobId, boardId, ownerId, PdfJob.Status.PENDING,
                ZonedDateTime.now(), ZonedDateTime.now());
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(boardRepo.findById(boardId)).thenReturn(Optional.of(
                new Board(boardId, ownerId, "Board", "Recipient", ZonedDateTime.now())));
        when(postRepo.findByBoardIdOrderByCreatedAtAsc(boardId)).thenReturn(List.of());

        PdfJobService service = new PdfJobService(boardRepo, postRepo, jobRepo);
        service.processJob(jobId);

        verify(jobRepo, atLeast(2)).save(any(PdfJob.class));
        assertThat(job.getStatus()).isEqualTo(PdfJob.Status.SUCCEEDED);
    }

    @Test
    void processJob_updatesStatusToFailedOnError() {
        UUID jobId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();

        PdfJobRepository jobRepo = mock(PdfJobRepository.class);
        BoardRepository boardRepo = mock(BoardRepository.class);

        PdfJob job = new PdfJob(jobId, boardId, "auth0|owner", PdfJob.Status.PENDING,
                ZonedDateTime.now(), ZonedDateTime.now());
        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(boardRepo.findById(boardId)).thenReturn(Optional.empty());

        PdfJobService service = new PdfJobService(boardRepo, mock(PostRepository.class), jobRepo);
        service.processJob(jobId);

        assertThat(job.getStatus()).isEqualTo(PdfJob.Status.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("GENERATION_ERROR");
    }

    private static PdfJobService createService() {
        return new PdfJobService(mock(BoardRepository.class), mock(PostRepository.class), mock(PdfJobRepository.class));
    }
}
