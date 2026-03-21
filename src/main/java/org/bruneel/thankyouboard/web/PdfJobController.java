package org.bruneel.thankyouboard.web;

import org.bruneel.thankyouboard.domain.Board;
import org.bruneel.thankyouboard.domain.PdfJob;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PdfJobRepository;
import org.bruneel.thankyouboard.service.BoardPdfRenderer;
import org.bruneel.thankyouboard.service.PdfJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/boards/{boardId}/pdf-jobs", version = "1")
public class PdfJobController {

    private static final Logger log = LoggerFactory.getLogger(PdfJobController.class);

    private final PdfJobService pdfJobService;
    private final PdfJobRepository pdfJobRepository;
    private final BoardRepository boardRepository;

    public PdfJobController(PdfJobService pdfJobService,
                            PdfJobRepository pdfJobRepository,
                            BoardRepository boardRepository) {
        this.pdfJobService = pdfJobService;
        this.pdfJobRepository = pdfJobRepository;
        this.boardRepository = boardRepository;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createPdfJob(
            @PathVariable UUID boardId,
            @RequestBody(required = false) CreatePdfJobRequest body,
            Authentication authentication) {

        String ownerId = requireSub(authentication);
        log.debug("PDF job creation requested for board {} by {}", boardId, ownerId);

        Optional<Board> boardOpt = boardRepository.findById(boardId);
        if (boardOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Board board = boardOpt.get();
        if (!ownerId.equals(board.getOwnerId())) {
            return ResponseEntity.status(403).build();
        }

        String timeZoneId = body != null ? body.timeZone() : null;
        PdfJob job = pdfJobService.createJob(boardId, ownerId, timeZoneId);
        pdfJobService.processJobAsync(job.getJobId());

        String statusUrl = "/api/boards/" + boardId + "/pdf-jobs/" + job.getJobId();
        Map<String, Object> responseBody = jobToJson(job, statusUrl, null);

        log.info("PDF job {} created for board {}", job.getJobId(), boardId);

        return ResponseEntity
                .accepted()
                .location(URI.create(statusUrl))
                .body(responseBody);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> getPdfJobStatus(
            @PathVariable UUID boardId,
            @PathVariable UUID jobId,
            Authentication authentication) {

        String ownerId = requireSub(authentication);

        Optional<PdfJob> jobOpt = pdfJobRepository.findByJobIdAndBoardId(jobId, boardId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PdfJob job = jobOpt.get();
        if (!ownerId.equals(job.getOwnerId())) {
            return ResponseEntity.status(403).build();
        }

        String statusUrl = "/api/boards/" + boardId + "/pdf-jobs/" + jobId;
        String downloadUrl = null;
        if (job.getStatus() == PdfJob.Status.SUCCEEDED) {
            downloadUrl = statusUrl + "/download";
        }

        return ResponseEntity.ok(jobToJson(job, statusUrl, downloadUrl));
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<Resource> downloadPdf(
            @PathVariable UUID boardId,
            @PathVariable UUID jobId,
            Authentication authentication) {

        String ownerId = requireSub(authentication);

        Optional<PdfJob> jobOpt = pdfJobRepository.findByJobIdAndBoardId(jobId, boardId);
        if (jobOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        PdfJob job = jobOpt.get();
        if (!ownerId.equals(job.getOwnerId())) {
            return ResponseEntity.status(403).build();
        }
        if (job.getStatus() != PdfJob.Status.SUCCEEDED || job.getDownloadPath() == null) {
            return ResponseEntity.notFound().build();
        }

        Path pdfPath = Path.of(job.getDownloadPath());
        if (!Files.exists(pdfPath)) {
            log.warn("PDF file missing on disk for job {}: {}", jobId, pdfPath);
            return ResponseEntity.notFound().build();
        }

        Board board = boardRepository.findById(boardId).orElse(null);
        String filename = board != null
                ? BoardPdfRenderer.toFilename(board)
                : jobId + ".pdf";

        Resource resource = new FileSystemResource(pdfPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    private static Map<String, Object> jobToJson(PdfJob job, String statusUrl, String downloadUrl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("jobId", job.getJobId().toString());
        map.put("boardId", job.getBoardId().toString());
        map.put("status", job.getStatus().name());
        map.put("statusUrl", statusUrl);
        if (downloadUrl != null) {
            map.put("downloadUrl", downloadUrl);
        }
        if (job.getErrorCode() != null) {
            map.put("errorCode", job.getErrorCode());
        }
        if (job.getErrorMessage() != null) {
            map.put("errorMessage", job.getErrorMessage());
        }
        map.put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt().toString() : null);
        map.put("updatedAt", job.getUpdatedAt() != null ? job.getUpdatedAt().toString() : null);
        return map;
    }

    /** Optional request body when creating a PDF job. timeZone: IANA zone id (e.g. "America/New_York") to show "Created" date in that zone. */
    private record CreatePdfJobRequest(String timeZone) {}

    private static String requireSub(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("Expected JwtAuthenticationToken for authenticated requests");
        }
        String sub = jwtAuth.getToken().getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException("JWT subject (sub) is missing");
        }
        return sub;
    }
}
