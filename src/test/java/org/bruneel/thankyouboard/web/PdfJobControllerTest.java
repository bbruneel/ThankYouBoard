package org.bruneel.thankyouboard.web;

import org.bruneel.thankyouboard.domain.Board;
import org.bruneel.thankyouboard.domain.PdfJob;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PdfJobRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.bruneel.thankyouboard.security.SecurityConfig;
import org.bruneel.thankyouboard.service.PdfJobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PdfJobController.class)
@Import(SecurityConfig.class)
class PdfJobControllerTest {

    private static final String ACCEPT_VERSION_1 = "application/json;version=1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PdfJobService pdfJobService;

    @MockitoBean
    private PdfJobRepository pdfJobRepository;

    @MockitoBean
    private BoardRepository boardRepository;

    @MockitoBean
    private PostRepository postRepository;

    @Test
    void createPdfJob_requiresAuthentication() throws Exception {
        UUID boardId = UUID.randomUUID();
        mockMvc.perform(post("/api/boards/" + boardId + "/pdf-jobs")
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPdfJob_returns404WhenBoardNotFound() throws Exception {
        UUID boardId = UUID.randomUUID();
        given(boardRepository.findById(boardId)).willReturn(Optional.empty());

        mockMvc.perform(post("/api/boards/" + boardId + "/pdf-jobs")
                        .with(jwt().jwt(j -> j.subject("auth0|owner")))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPdfJob_returns403WhenNotOwner() throws Exception {
        UUID boardId = UUID.randomUUID();
        Board board = createBoard(boardId, "auth0|owner", "Test Board", "Recipient");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        mockMvc.perform(post("/api/boards/" + boardId + "/pdf-jobs")
                        .with(jwt().jwt(j -> j.subject("auth0|other")))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isForbidden());
    }

    @Test
    void createPdfJob_returns202ForOwner() throws Exception {
        UUID boardId = UUID.randomUUID();
        String ownerSub = "auth0|owner";
        Board board = createBoard(boardId, ownerSub, "Test Board", "Recipient");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        PdfJob job = createJob(UUID.randomUUID(), boardId, ownerSub, PdfJob.Status.PENDING);
        given(pdfJobService.createJob(eq(boardId), eq(ownerSub), org.mockito.ArgumentMatchers.any())).willReturn(job);

        mockMvc.perform(post("/api/boards/" + boardId + "/pdf-jobs")
                        .with(jwt().jwt(j -> j.subject(ownerSub)))
                        .accept(ACCEPT_VERSION_1)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.jobId").value(job.getJobId().toString()))
                .andExpect(jsonPath("$.boardId").value(boardId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.statusUrl").isNotEmpty());

        verify(pdfJobService).processJobAsync(job.getJobId());
    }

    @Test
    void getPdfJobStatus_requiresAuthentication() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        mockMvc.perform(get("/api/boards/" + boardId + "/pdf-jobs/" + jobId)
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPdfJobStatus_returns404WhenNotFound() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        given(pdfJobRepository.findByJobIdAndBoardId(jobId, boardId)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/boards/" + boardId + "/pdf-jobs/" + jobId)
                        .with(jwt().jwt(j -> j.subject("auth0|owner")))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPdfJobStatus_returns403WhenNotOwner() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        PdfJob job = createJob(jobId, boardId, "auth0|owner", PdfJob.Status.RUNNING);
        given(pdfJobRepository.findByJobIdAndBoardId(jobId, boardId)).willReturn(Optional.of(job));

        mockMvc.perform(get("/api/boards/" + boardId + "/pdf-jobs/" + jobId)
                        .with(jwt().jwt(j -> j.subject("auth0|other")))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPdfJobStatus_returnsPendingJob() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String ownerSub = "auth0|owner";
        PdfJob job = createJob(jobId, boardId, ownerSub, PdfJob.Status.PENDING);
        given(pdfJobRepository.findByJobIdAndBoardId(jobId, boardId)).willReturn(Optional.of(job));

        mockMvc.perform(get("/api/boards/" + boardId + "/pdf-jobs/" + jobId)
                        .with(jwt().jwt(j -> j.subject(ownerSub)))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.downloadUrl").doesNotExist());
    }

    @Test
    void getPdfJobStatus_returnsSucceededJobWithDownloadUrl() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String ownerSub = "auth0|owner";
        PdfJob job = createJob(jobId, boardId, ownerSub, PdfJob.Status.SUCCEEDED);
        job.setDownloadPath("/tmp/test.pdf");
        given(pdfJobRepository.findByJobIdAndBoardId(jobId, boardId)).willReturn(Optional.of(job));

        mockMvc.perform(get("/api/boards/" + boardId + "/pdf-jobs/" + jobId)
                        .with(jwt().jwt(j -> j.subject(ownerSub)))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty());
    }

    @Test
    void getPdfJobStatus_returnsFailedJobWithError() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String ownerSub = "auth0|owner";
        PdfJob job = createJob(jobId, boardId, ownerSub, PdfJob.Status.FAILED);
        job.setErrorCode("GENERATION_ERROR");
        job.setErrorMessage("Out of memory");
        given(pdfJobRepository.findByJobIdAndBoardId(jobId, boardId)).willReturn(Optional.of(job));

        mockMvc.perform(get("/api/boards/" + boardId + "/pdf-jobs/" + jobId)
                        .with(jwt().jwt(j -> j.subject(ownerSub)))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("GENERATION_ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("Out of memory"));
    }

    @Test
    void downloadPdf_requiresAuthentication() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        mockMvc.perform(get("/api/boards/" + boardId + "/pdf-jobs/" + jobId + "/download")
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadPdf_returns404WhenJobNotFound() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        given(pdfJobRepository.findByJobIdAndBoardId(jobId, boardId)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/boards/" + boardId + "/pdf-jobs/" + jobId + "/download")
                        .with(jwt().jwt(j -> j.subject("auth0|owner")))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadPdf_returns404WhenJobNotSucceeded() throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String ownerSub = "auth0|owner";
        PdfJob job = createJob(jobId, boardId, ownerSub, PdfJob.Status.RUNNING);
        given(pdfJobRepository.findByJobIdAndBoardId(jobId, boardId)).willReturn(Optional.of(job));

        mockMvc.perform(get("/api/boards/" + boardId + "/pdf-jobs/" + jobId + "/download")
                        .with(jwt().jwt(j -> j.subject(ownerSub)))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadPdf_servesFileForSucceededJob(@TempDir Path tempDir) throws Exception {
        UUID boardId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String ownerSub = "auth0|owner";

        Path pdfFile = tempDir.resolve("test.pdf");
        java.nio.file.Files.writeString(pdfFile, "%PDF-1.4 test content");

        PdfJob job = createJob(jobId, boardId, ownerSub, PdfJob.Status.SUCCEEDED);
        job.setDownloadPath(pdfFile.toString());
        given(pdfJobRepository.findByJobIdAndBoardId(jobId, boardId)).willReturn(Optional.of(job));

        Board board = createBoard(boardId, ownerSub, "Test Board", "Recipient");
        given(boardRepository.findById(boardId)).willReturn(Optional.of(board));

        mockMvc.perform(get("/api/boards/" + boardId + "/pdf-jobs/" + jobId + "/download")
                        .with(jwt().jwt(j -> j.subject(ownerSub)))
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".pdf")));
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

    private static PdfJob createJob(UUID jobId, UUID boardId, String ownerId, PdfJob.Status status) {
        PdfJob job = new PdfJob();
        job.setJobId(jobId);
        job.setBoardId(boardId);
        job.setOwnerId(ownerId);
        job.setStatus(status);
        job.setCreatedAt(ZonedDateTime.now());
        job.setUpdatedAt(ZonedDateTime.now());
        return job;
    }
}
