package org.bruneel.thankyouboard.service;

import org.bruneel.thankyouboard.domain.Board;
import org.bruneel.thankyouboard.domain.PdfJob;
import org.bruneel.thankyouboard.domain.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PdfJobRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.bruneel.thankyouboard.validation.ImageUrlPolicy;
import org.bruneel.thankyouboard.validation.TimeZoneValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PdfJobService {

    private static final Logger log = LoggerFactory.getLogger(PdfJobService.class);

    private final PdfJobRepository pdfJobRepository;
    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final BoardPdfRenderer.RenderConfig pdfRenderConfig;
    private final Path storageDir;

    @Autowired
    public PdfJobService(
            PdfJobRepository pdfJobRepository,
            BoardRepository boardRepository,
            PostRepository postRepository,
            @Value("${boards.images.allowed-hosts:" + ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS + "}") String allowedImageHosts,
            @Value("${boards.pdf.image-fetch-timeout:2s}") java.time.Duration imageFetchTimeout,
            @Value("${boards.pdf.max-image-bytes-per-image:1048576}") int maxImageBytesPerImage,
            @Value("${boards.pdf.max-image-bytes-total:10485760}") int maxImageBytesTotal,
            @Value("${boards.pdf.storage-dir:${java.io.tmpdir}/pdf-exports}") String storageDir) {
        this.pdfJobRepository = pdfJobRepository;
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
        this.pdfRenderConfig = new BoardPdfRenderer.RenderConfig(
                ImageUrlPolicy.fromCsv(allowedImageHosts),
                imageFetchTimeout,
                maxImageBytesPerImage,
                maxImageBytesTotal
        );
        this.storageDir = Path.of(storageDir);
    }

    PdfJobService(PdfJobRepository pdfJobRepository, BoardRepository boardRepository,
                  PostRepository postRepository, Path storageDir) {
        this.pdfJobRepository = pdfJobRepository;
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
        this.pdfRenderConfig = BoardPdfRenderer.RenderConfig.defaults();
        this.storageDir = storageDir;
    }

    public PdfJob createJob(UUID boardId, String ownerId) {
        return createJob(boardId, ownerId, null);
    }

    public PdfJob createJob(UUID boardId, String ownerId, String timeZoneId) {
        PdfJob job = new PdfJob();
        job.setBoardId(boardId);
        job.setOwnerId(ownerId);
        job.setStatus(PdfJob.Status.PENDING);
        if (timeZoneId != null && !timeZoneId.isBlank()) {
            String sanitized = TimeZoneValidator.sanitizeForStore(timeZoneId);
            if (sanitized != null) {
                job.setTimeZone(sanitized);
            }
        }
        ZonedDateTime now = ZonedDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return pdfJobRepository.save(job);
    }

    @Async("pdfJobExecutor")
    public void processJobAsync(UUID jobId) {
        processJob(jobId);
    }

    void processJob(UUID jobId) {
        PdfJob job = pdfJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("PDF job {} not found", jobId);
            return;
        }

        job.setStatus(PdfJob.Status.RUNNING);
        job.setUpdatedAt(ZonedDateTime.now());
        pdfJobRepository.save(job);

        try {
            Board board = boardRepository.findById(job.getBoardId())
                    .orElseThrow(() -> new IllegalStateException("Board not found: " + job.getBoardId()));

            List<Post> posts = postRepository.findByBoardIdOrderByCreatedAtAsc(job.getBoardId());
            ZoneId displayZone = TimeZoneValidator.parse(job.getTimeZone());
            byte[] pdfBytes = BoardPdfRenderer.render(board, posts, pdfRenderConfig, displayZone);

            Path boardDir = storageDir.resolve(job.getBoardId().toString());
            Files.createDirectories(boardDir);
            String filename = BoardPdfRenderer.toFilename(board);
            Path pdfPath = boardDir.resolve(job.getJobId() + ".pdf");
            Files.write(pdfPath, pdfBytes);

            job.setStatus(PdfJob.Status.SUCCEEDED);
            job.setDownloadPath(pdfPath.toString());
            job.setUpdatedAt(ZonedDateTime.now());
            pdfJobRepository.save(job);

            log.info("PDF job {} succeeded: {} bytes written to {}", jobId, pdfBytes.length, pdfPath);
        } catch (IOException e) {
            log.error("PDF job {} failed with I/O error", jobId, e);
            failJob(job, "IO_ERROR", e.getMessage());
        } catch (Exception e) {
            log.error("PDF job {} failed", jobId, e);
            failJob(job, "GENERATION_ERROR", e.getMessage());
        }
    }

    private void failJob(PdfJob job, String errorCode, String errorMessage) {
        job.setStatus(PdfJob.Status.FAILED);
        job.setErrorCode(errorCode);
        job.setErrorMessage(errorMessage != null && errorMessage.length() > 500
                ? errorMessage.substring(0, 500) : errorMessage);
        job.setUpdatedAt(ZonedDateTime.now());
        pdfJobRepository.save(job);
    }
}
