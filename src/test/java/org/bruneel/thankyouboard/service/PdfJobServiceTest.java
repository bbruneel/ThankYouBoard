package org.bruneel.thankyouboard.service;

import org.bruneel.thankyouboard.domain.Board;
import org.bruneel.thankyouboard.domain.PdfJob;
import org.bruneel.thankyouboard.domain.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PdfJobRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfJobServiceTest {

    @Mock
    private PdfJobRepository pdfJobRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private PostRepository postRepository;

    @TempDir
    Path storageDir;

    private PdfJobService pdfJobService;

    @BeforeEach
    void setUp() {
        pdfJobService = new PdfJobService(pdfJobRepository, boardRepository, postRepository, storageDir);
    }

    @Test
    void createJob_setsPendingAndPersists() {
        UUID boardId = UUID.randomUUID();
        String owner = "auth0|owner";

        when(pdfJobRepository.save(any(PdfJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PdfJob saved = pdfJobService.createJob(boardId, owner);

        assertThat(saved.getBoardId()).isEqualTo(boardId);
        assertThat(saved.getOwnerId()).isEqualTo(owner);
        assertThat(saved.getStatus()).isEqualTo(PdfJob.Status.PENDING);
        verify(pdfJobRepository).save(any(PdfJob.class));
    }

    @Test
    void createJob_withValidTimeZone_setsTimeZone() {
        UUID boardId = UUID.randomUUID();
        when(pdfJobRepository.save(any(PdfJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PdfJob saved = pdfJobService.createJob(boardId, "auth0|o", "Europe/Brussels");

        assertThat(saved.getTimeZone()).isNotBlank();
    }

    @Test
    void processJob_whenJobMissing_returnsEarly() {
        UUID jobId = UUID.randomUUID();
        when(pdfJobRepository.findById(jobId)).thenReturn(Optional.empty());

        pdfJobService.processJob(jobId);

        verify(pdfJobRepository).findById(jobId);
        verify(pdfJobRepository, never()).save(any());
    }

    @Test
    void processJob_whenBoardMissing_marksFailed() {
        UUID jobId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        PdfJob job = new PdfJob();
        job.setJobId(jobId);
        job.setBoardId(boardId);
        job.setOwnerId("auth0|o");
        job.setStatus(PdfJob.Status.PENDING);

        when(pdfJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(boardRepository.findById(boardId)).thenReturn(Optional.empty());

        pdfJobService.processJob(jobId);

        ArgumentCaptor<PdfJob> captor = ArgumentCaptor.forClass(PdfJob.class);
        verify(pdfJobRepository, atLeast(2)).save(captor.capture());
        PdfJob last = captor.getValue();
        assertThat(last.getStatus()).isEqualTo(PdfJob.Status.FAILED);
        assertThat(last.getErrorCode()).isEqualTo("GENERATION_ERROR");
    }

    @Test
    void processJob_success_writesPdfFile() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        PdfJob job = new PdfJob();
        job.setJobId(jobId);
        job.setBoardId(boardId);
        job.setOwnerId("auth0|o");
        job.setStatus(PdfJob.Status.PENDING);

        Board board = new Board();
        board.setId(boardId);
        board.setOwnerId("auth0|o");
        board.setTitle("My Board");
        board.setRecipientName("R");
        board.setCreatedAt(ZonedDateTime.now());

        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setBoardId(boardId);
        post.setAuthorName("A");
        post.setMessageText("Hi");
        post.setCreatedAt(ZonedDateTime.now());

        when(pdfJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
        when(postRepository.findByBoardIdOrderByCreatedAtAsc(boardId)).thenReturn(List.of(post));

        pdfJobService.processJob(jobId);

        ArgumentCaptor<PdfJob> captor = ArgumentCaptor.forClass(PdfJob.class);
        verify(pdfJobRepository, atLeast(2)).save(captor.capture());
        PdfJob finished = captor.getValue();
        assertThat(finished.getStatus()).isEqualTo(PdfJob.Status.SUCCEEDED);
        assertThat(finished.getDownloadPath()).isNotNull();
        assertThat(Files.exists(Path.of(finished.getDownloadPath()))).isTrue();
        assertThat(Files.readAllBytes(Path.of(finished.getDownloadPath()))).isNotEmpty();
    }
}
