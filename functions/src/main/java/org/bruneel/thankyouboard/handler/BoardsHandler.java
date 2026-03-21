package org.bruneel.thankyouboard.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PdfJobRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.bruneel.thankyouboard.service.BoardsService;
import org.bruneel.thankyouboard.service.PdfJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;
import software.amazon.lambda.powertools.tracing.TracingUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BoardsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(BoardsHandler.class);
    private static final Pattern PDF_JOBS_STATUS_PATTERN = Pattern.compile("/api/boards/[^/]+/pdf-jobs/([^/]+)$");

    private final BoardsService boardsService;
    private final PdfJobService pdfJobService;

    public BoardsHandler() {
        String boardsTable = System.getenv("BOARDS_TABLE");
        String postsTable = System.getenv("POSTS_TABLE");
        if (boardsTable == null || postsTable == null) {
            throw new IllegalStateException("BOARDS_TABLE and POSTS_TABLE must be set");
        }

        BoardRepository boardRepo = new BoardRepository(boardsTable);
        PostRepository postRepo = new PostRepository(postsTable);
        this.boardsService = new BoardsService(boardRepo, postRepo);

        String pdfJobsTable = System.getenv("PDF_JOBS_TABLE");
        String pdfBucket = System.getenv("PDF_BUCKET");
        String sqsQueueUrl = System.getenv("PDF_QUEUE_URL");
        if (pdfJobsTable != null && pdfBucket != null && sqsQueueUrl != null) {
            SqsClient sqs = SqsClient.builder()
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .addExecutionInterceptor(new com.amazonaws.xray.interceptors.TracingInterceptor())
                            .build())
                    .build();
            S3Client s3 = S3Client.builder()
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .addExecutionInterceptor(new com.amazonaws.xray.interceptors.TracingInterceptor())
                            .build())
                    .build();
            this.pdfJobService = new PdfJobService(
                    boardRepo, postRepo,
                    new PdfJobRepository(pdfJobsTable),
                    sqs, sqsQueueUrl,
                    s3, S3Presigner.create(), pdfBucket);
        } else {
            this.pdfJobService = null;
        }
    }

    BoardsHandler(BoardsService boardsService) {
        this.boardsService = boardsService;
        this.pdfJobService = null;
    }

    BoardsHandler(BoardsService boardsService, PdfJobService pdfJobService) {
        this.boardsService = boardsService;
        this.pdfJobService = pdfJobService;
    }

    @Override
    @Logging(clearState = true)
    @Tracing
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String correlationId = CorrelationContext.init(event, context);

        String method = event.getHttpMethod();
        log.info("Boards {} {}", method, event.getPath());

        if (!ResponseUtil.validateAcceptHeader(event)) {
            APIGatewayProxyResponseEvent resp = ResponseUtil.jsonResponse(400,
                    Map.of("error", "Missing or invalid Accept header. Required: application/json; version=1"));
            ResponseUtil.stampCorrelationId(resp, correlationId);
            return resp;
        }

        APIGatewayProxyResponseEvent response;
        String path = event.getPath();
        String id = null;
        try {
            Map<String, String> pathParams = event.getPathParameters();
            id = pathParams != null ? pathParams.get("id") : null;

            if (id != null) {
                MDC.put("board_id", id);
                TracingUtils.putAnnotation("board_id", id);
            }

            if (path != null && path.contains("/pdf-jobs") && pdfJobService != null) {
                String ownerId = extractSub(event);
                Matcher jobMatcher = PDF_JOBS_STATUS_PATTERN.matcher(path);
                if ("GET".equals(method) && jobMatcher.matches()) {
                    String jobId = jobMatcher.group(1);
                    MDC.put("job_id", jobId);
                    TracingUtils.putAnnotation("job_id", jobId);
                    response = pdfJobService.getPdfJobStatus(id, jobId, ownerId);
                } else if ("POST".equals(method) && id != null && path.endsWith("/pdf-jobs")) {
                    response = pdfJobService.createPdfJob(id, ownerId, correlationId);
                } else {
                    log.warn("Method not allowed: {} {}. For pdf-jobs use GET (status) or POST (create).", method, path);
                    response = ResponseUtil.jsonResponse(405, Map.of("error", "Method not allowed"));
                }
            } else if ("GET".equals(method) && id != null && path != null && path.endsWith("/pdf")) {
                String ownerId = extractSub(event);
                response = boardsService.downloadBoardPdf(id, ownerId);
            } else if ("GET".equals(method) && id != null) {
                String requesterSub = extractSub(event);
                if (requesterSub == null || requesterSub.isBlank()) {
                    requesterSub = extractSubFromAuthorizationHeader(event);
                }
                response = boardsService.getBoard(id, requesterSub);
            } else if ("GET".equals(method)) {
                String ownerId = extractSub(event);
                response = boardsService.listBoards(ownerId);
            } else if ("POST".equals(method)) {
                String ownerId = extractSub(event);
                response = boardsService.createBoard(event.getBody(), ownerId);
            } else if ("PUT".equals(method) && id != null) {
                String ownerId = extractSub(event);
                response = boardsService.updateBoard(id, event.getBody(), ownerId);
            } else if ("DELETE".equals(method) && id != null) {
                String ownerId = extractSub(event);
                response = boardsService.deleteBoard(id, ownerId);
            } else {
                log.warn("Method not allowed: {} {}. Allowed: GET (list/get), POST (create), PUT (update), DELETE.", method, path);
                response = ResponseUtil.jsonResponse(405, Map.of("error", "Method not allowed"));
            }
        } catch (Exception e) {
            log.error("Error handling boards request", e);
            response = ResponseUtil.jsonResponse(500,
                    Map.of("error", "Internal server error", "correlationId", correlationId));
        }

        ResponseUtil.stampCorrelationId(response, correlationId);
        MDC.put("status", String.valueOf(response.getStatusCode()));
        int status = response.getStatusCode();
        if (status >= 400 && status < 500) {
            log.info("Boards response status={} path={} boardId={}", status, path, id);
        } else {
            log.info("Boards response status={}", status);
        }
        return response;
    }

    private static String extractSub(APIGatewayProxyRequestEvent event) {
        if (event == null || event.getRequestContext() == null) return null;
        Map<String, Object> authorizer = event.getRequestContext().getAuthorizer();
        if (authorizer == null) return null;

        Object claimsObj = authorizer.get("claims");
        if (claimsObj instanceof Map<?, ?> claims) {
            Object sub = claims.get("sub");
            return sub != null ? sub.toString() : null;
        }

        Object jwtObj = authorizer.get("jwt");
        if (jwtObj instanceof Map<?, ?> jwt) {
            Object jwtClaimsObj = jwt.get("claims");
            if (jwtClaimsObj instanceof Map<?, ?> jwtClaims) {
                Object sub = jwtClaims.get("sub");
                return sub != null ? sub.toString() : null;
            }
        }

        return null;
    }

    private static String extractSubFromAuthorizationHeader(APIGatewayProxyRequestEvent event) {
        if (event == null) return null;
        Map<String, String> headers = event.getHeaders();
        if (headers == null || headers.isEmpty()) return null;

        String auth = headers.get("Authorization");
        if (auth == null) {
            auth = headers.get("authorization");
        }
        if (auth == null) return null;

        String prefix = "Bearer ";
        if (!auth.startsWith(prefix)) return null;
        String token = auth.substring(prefix.length()).trim();
        if (token.isBlank()) return null;

        // Best-effort decode of JWT payload for UI hints (canEdit); secured endpoints still rely on API Gateway authorizer.
        // Do not treat this as proof of authentication.
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            byte[] jsonBytes = Base64.getUrlDecoder().decode(parts[1]);
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = new com.google.gson.Gson().fromJson(json, Map.class);
            Object sub = payload.get("sub");
            return sub != null ? sub.toString() : null;
        } catch (Exception e) {
            log.debug("Failed to decode JWT payload sub from Authorization header", e);
            return null;
        }
    }

}
