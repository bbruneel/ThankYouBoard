package org.bruneel.thankyouboard.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.bruneel.thankyouboard.handler.ResponseUtil;
import org.bruneel.thankyouboard.model.Board;
import org.bruneel.thankyouboard.model.PdfJob;
import org.bruneel.thankyouboard.model.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PdfJobRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

public class PdfJobService {

    private static final Logger log = LoggerFactory.getLogger(PdfJobService.class);

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final PdfJobRepository pdfJobRepository;
    private final SqsClient sqsClient;
    private final String sqsQueueUrl;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String pdfBucketName;
    private final BoardPdfRenderer.RenderConfig pdfRenderConfig;

    public PdfJobService(BoardRepository boardRepository, PostRepository postRepository,
                         PdfJobRepository pdfJobRepository,
                         SqsClient sqsClient, String sqsQueueUrl,
                         S3Client s3Client, S3Presigner s3Presigner, String pdfBucketName) {
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
        this.pdfJobRepository = pdfJobRepository;
        this.sqsClient = sqsClient;
        this.sqsQueueUrl = sqsQueueUrl;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.pdfBucketName = pdfBucketName;
        this.pdfRenderConfig = BoardPdfRenderer.RenderConfig.fromEnvironment();
    }

    PdfJobService(BoardRepository boardRepository, PostRepository postRepository,
                  PdfJobRepository pdfJobRepository) {
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
        this.pdfJobRepository = pdfJobRepository;
        this.sqsClient = null;
        this.sqsQueueUrl = null;
        this.s3Client = null;
        this.s3Presigner = null;
        this.pdfBucketName = null;
        this.pdfRenderConfig = BoardPdfRenderer.RenderConfig.defaults();
    }

    @Tracing(namespace = "PdfJobService")
    public APIGatewayProxyResponseEvent createPdfJob(String boardIdStr, String ownerId) {
        return createPdfJob(boardIdStr, ownerId, null);
    }

    @Tracing(namespace = "PdfJobService")
    public APIGatewayProxyResponseEvent createPdfJob(String boardIdStr, String ownerId, String correlationId) {
        if (ownerId == null || ownerId.isBlank()) {
            log.info("PDF job request rejected: caller identity missing. Send valid JWT.");
            return ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized"));
        }

        UUID boardId;
        try {
            boardId = UUID.fromString(boardIdStr);
        } catch (IllegalArgumentException e) {
            log.info("PDF job request rejected: invalid UUID for board or job. boardId={}, jobId=(create).", boardIdStr);
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid UUID"));
        }

        return boardRepository.findById(boardId)
                .map(board -> {
                    if (!ownerId.equals(board.getOwnerId())) {
                        log.info("PDF job access forbidden: caller is not board/job owner. boardId={}.", boardId);
                        return ResponseUtil.jsonResponse(403, Map.of("error", "Forbidden"));
                    }

                    ZonedDateTime now = ZonedDateTime.now();
                    PdfJob job = new PdfJob(UUID.randomUUID(), boardId, ownerId,
                            PdfJob.Status.PENDING, now, now);
                    pdfJobRepository.save(job);

                    if (sqsClient != null && sqsQueueUrl != null) {
                        SendMessageRequest.Builder msgBuilder = SendMessageRequest.builder()
                                .queueUrl(sqsQueueUrl)
                                .messageBody(job.getJobId().toString());

                        if (correlationId != null && !correlationId.isBlank()) {
                            msgBuilder.messageAttributes(Map.of(
                                    "correlation_id", MessageAttributeValue.builder()
                                            .dataType("String")
                                            .stringValue(correlationId)
                                            .build()));
                        }

                        sqsClient.sendMessage(msgBuilder.build());
                    }

                    String statusUrl = "/api/boards/" + boardId + "/pdf-jobs/" + job.getJobId();
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("jobId", job.getJobId().toString());
                    body.put("boardId", boardId.toString());
                    body.put("status", job.getStatus().name());
                    body.put("statusUrl", statusUrl);
                    body.put("createdAt", now.toString());

                    APIGatewayProxyResponseEvent response = ResponseUtil.jsonResponse(202, body);
                    Map<String, String> headers = new HashMap<>(response.getHeaders());
                    headers.put("Location", statusUrl);
                    response.setHeaders(headers);
                    return response;
                })
                .orElseGet(() -> {
                    log.info("PDF job create rejected: board not found. boardId={}.", boardId);
                    return ResponseUtil.jsonResponse(404, Map.of("error", "Board not found"));
                });
    }

    @Tracing(namespace = "PdfJobService")
    public APIGatewayProxyResponseEvent getPdfJobStatus(String boardIdStr, String jobIdStr, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            log.info("PDF job request rejected: caller identity missing. Send valid JWT.");
            return ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized"));
        }

        UUID jobId;
        UUID boardId;
        try {
            jobId = UUID.fromString(jobIdStr);
            boardId = UUID.fromString(boardIdStr);
        } catch (IllegalArgumentException e) {
            log.info("PDF job request rejected: invalid UUID for board or job. boardId={}, jobId={}.", boardIdStr, jobIdStr);
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid UUID"));
        }

        return pdfJobRepository.findById(jobId)
                .map(job -> {
                    if (!boardId.equals(job.getBoardId())) {
                        log.info("PDF job status not found: jobId does not match boardId. jobId={}, boardId={}.", jobIdStr, boardIdStr);
                        return ResponseUtil.jsonResponse(404, Map.of("error", "Job not found"));
                    }
                    if (!ownerId.equals(job.getOwnerId())) {
                        log.info("PDF job access forbidden: caller is not board/job owner. jobId={}.", jobId);
                        return ResponseUtil.jsonResponse(403, Map.of("error", "Forbidden"));
                    }

                    String statusUrl = "/api/boards/" + boardId + "/pdf-jobs/" + jobId;
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("jobId", jobId.toString());
                    body.put("boardId", boardId.toString());
                    body.put("status", job.getStatus().name());
                    body.put("statusUrl", statusUrl);

                    if (job.getStatus() == PdfJob.Status.SUCCEEDED && job.getDownloadKey() != null
                            && s3Presigner != null && pdfBucketName != null) {
                        String presignedUrl = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(15))
                                .getObjectRequest(b -> b.bucket(pdfBucketName).key(job.getDownloadKey()))
                                .build()).url().toString();
                        body.put("downloadUrl", presignedUrl);
                    }

                    if (job.getErrorCode() != null) body.put("errorCode", job.getErrorCode());
                    if (job.getErrorMessage() != null) body.put("errorMessage", job.getErrorMessage());
                    body.put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt().toString() : null);
                    body.put("updatedAt", job.getUpdatedAt() != null ? job.getUpdatedAt().toString() : null);

                    return ResponseUtil.jsonResponse(200, body);
                })
                .orElseGet(() -> {
                    log.info("PDF job status not found: jobId={}, boardId={}. Job may not exist or was not created for this board.", jobIdStr, boardIdStr);
                    return ResponseUtil.jsonResponse(404, Map.of("error", "Job not found"));
                });
    }

    @Tracing(namespace = "PdfJobService")
    public void processJob(UUID jobId) {
        PdfJob job = pdfJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("PDF job not found in queue processing. jobId={}. Message may have been invalid or job record missing.", jobId);
            return;
        }

        job.setStatus(PdfJob.Status.RUNNING);
        job.setUpdatedAt(ZonedDateTime.now());
        pdfJobRepository.save(job);

        try {
            Board board = boardRepository.findById(job.getBoardId())
                    .orElseThrow(() -> new IllegalStateException("Board not found: " + job.getBoardId()));

            List<Post> posts = postRepository.findByBoardIdOrderByCreatedAtAsc(job.getBoardId());
            byte[] pdfBytes = BoardPdfRenderer.render(board, posts, pdfRenderConfig);

            String s3Key = "pdf-exports/" + job.getBoardId() + "/" + job.getJobId() + ".pdf";

            if (s3Client != null && pdfBucketName != null) {
                String rawTitle = board.getTitle() != null ? board.getTitle() : "board";
                String safeTitle = rawTitle.toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");
                if (safeTitle.isBlank()) {
                    safeTitle = "board";
                }
                String fileName = safeTitle + "-" + job.getBoardId() + ".pdf";

                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(pdfBucketName)
                                .key(s3Key)
                                .contentType("application/pdf")
                                .contentDisposition("attachment; filename=\"" + fileName + "\"")
                                .build(),
                        RequestBody.fromBytes(pdfBytes));
            }

            job.setStatus(PdfJob.Status.SUCCEEDED);
            job.setDownloadKey(s3Key);
            job.setUpdatedAt(ZonedDateTime.now());
            pdfJobRepository.save(job);

            log.info("PDF job {} succeeded: {} bytes -> s3://{}/{}", jobId, pdfBytes.length, pdfBucketName, s3Key);
        } catch (Exception e) {
            log.error("PDF job {} failed", jobId, e);
            job.setStatus(PdfJob.Status.FAILED);
            job.setErrorCode("GENERATION_ERROR");
            String msg = e.getMessage();
            job.setErrorMessage(msg != null && msg.length() > 500 ? msg.substring(0, 500) : msg);
            job.setUpdatedAt(ZonedDateTime.now());
            pdfJobRepository.save(job);
        }
    }
}
