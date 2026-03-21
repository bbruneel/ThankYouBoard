package org.bruneel.thankyouboard.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bruneel.thankyouboard.handler.ResponseUtil;
import org.bruneel.thankyouboard.handler.ZonedDateTimeAdapter;
import org.bruneel.thankyouboard.model.Board;
import org.bruneel.thankyouboard.model.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BoardsService {

    private static final Logger log = LoggerFactory.getLogger(BoardsService.class);

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
            .create();

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final BoardPdfRenderer.RenderConfig pdfRenderConfig;
    private final int maxBoardsPerOwner;

    public BoardsService(String boardsTable, String postsTable) {
        this(
                new BoardRepository(boardsTable),
                postsTable != null ? new PostRepository(postsTable) : null,
                BoardPdfRenderer.RenderConfig.fromEnvironment(),
                parseMaxBoardsPerOwner(System.getenv("MAX_BOARDS_PER_OWNER"))
        );
    }

    public BoardsService(BoardRepository boardRepository, PostRepository postRepository) {
        this(
                boardRepository,
                postRepository,
                BoardPdfRenderer.RenderConfig.fromEnvironment(),
                parseMaxBoardsPerOwner(System.getenv("MAX_BOARDS_PER_OWNER"))
        );
    }

    BoardsService(BoardRepository boardRepository) {
        this(boardRepository, null, BoardPdfRenderer.RenderConfig.defaults(), parseMaxBoardsPerOwner(System.getenv("MAX_BOARDS_PER_OWNER")));
    }

    BoardsService(BoardRepository boardRepository, int maxBoardsPerOwner) {
        this(boardRepository, null, BoardPdfRenderer.RenderConfig.defaults(), maxBoardsPerOwner);
    }

    BoardsService(BoardRepository boardRepository,
                  PostRepository postRepository,
                  BoardPdfRenderer.RenderConfig pdfRenderConfig,
                  int maxBoardsPerOwner) {
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
        this.pdfRenderConfig = pdfRenderConfig;
        if (maxBoardsPerOwner < 1) {
            throw new IllegalArgumentException("MAX_BOARDS_PER_OWNER must be >= 1");
        }
        this.maxBoardsPerOwner = maxBoardsPerOwner;
    }

    @Tracing(namespace = "BoardsService")
    public APIGatewayProxyResponseEvent createBoard(String body, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            log.info("Board request rejected: missing or invalid caller identity (ownerId). Client must send a valid JWT.");
            return ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized"));
        }
        if (body == null || body.isBlank()) {
            log.info("Board create/update rejected: request body required.");
            return ResponseUtil.jsonResponse(400, Map.of("error", "Request body required"));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = GSON.fromJson(body, Map.class);
            String title = (String) json.get("title");
            String recipientName = (String) json.get("recipientName");
            if (title == null || title.isBlank() || recipientName == null || recipientName.isBlank()) {
                log.info("Board create/update rejected: title and recipientName are required.");
                return ResponseUtil.jsonResponse(400, Map.of("error", "title and recipientName required"));
            }
            if (title.length() > ValidationConstants.MAX_SHORT_TEXT_LENGTH) {
                log.info("Board rejected: title or recipientName exceeds max length. Max is {} characters.", ValidationConstants.MAX_SHORT_TEXT_LENGTH);
                return ResponseUtil.jsonResponse(400, Map.of("error", "title must be at most " + ValidationConstants.MAX_SHORT_TEXT_LENGTH + " characters"));
            }
            if (recipientName.length() > ValidationConstants.MAX_SHORT_TEXT_LENGTH) {
                log.info("Board rejected: title or recipientName exceeds max length. Max is {} characters.", ValidationConstants.MAX_SHORT_TEXT_LENGTH);
                return ResponseUtil.jsonResponse(400, Map.of("error", "recipientName must be at most " + ValidationConstants.MAX_SHORT_TEXT_LENGTH + " characters"));
            }

            long currentBoards = boardRepository.countByOwnerId(ownerId);
            if (currentBoards >= maxBoardsPerOwner) {
                log.warn("Board request rejected: board limit reached. ownerId={}, boardCount={}, limit={}", ownerId, currentBoards, maxBoardsPerOwner);
                return ResponseUtil.jsonResponse(409, Map.of(
                        "error", "Board limit reached (" + maxBoardsPerOwner + ")."
                ));
            }

            Board board = new Board(UUID.randomUUID(), ownerId, title, recipientName, ZonedDateTime.now());
            boardRepository.save(board);
            return ResponseUtil.jsonResponse(200, board);
        } catch (Exception e) {
            log.warn("Board create rejected: invalid JSON. {}", e.getMessage());
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid JSON: " + e.getMessage()));
        }
    }

    @Tracing(namespace = "BoardsService")
    public APIGatewayProxyResponseEvent listBoards(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            log.info("Board request rejected: missing or invalid caller identity (ownerId). Client must send a valid JWT.");
            return ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized"));
        }
        List<Board> boards = boardRepository.findByOwnerId(ownerId);
        return ResponseUtil.jsonResponse(200, boards);
    }

    public APIGatewayProxyResponseEvent getBoard(String idStr) {
        return getBoard(idStr, null);
    }

    @Tracing(namespace = "BoardsService")
    public APIGatewayProxyResponseEvent getBoard(String idStr, String requesterSub) {
        UUID id;
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            log.info("Invalid board ID format: '{}'. Board ID must be a valid UUID.", idStr);
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid UUID"));
        }
        return boardRepository.findById(id)
                .map(b -> {
                    boolean canEdit = requesterSub != null
                            && !requesterSub.isBlank()
                            && requesterSub.equals(b.getOwnerId());
                    BoardResponse response = new BoardResponse(
                            b.getId(),
                            b.getTitle(),
                            b.getRecipientName(),
                            b.getCreatedAt(),
                            canEdit
                    );
                    return ResponseUtil.jsonResponse(200, response);
                })
                .orElseGet(() -> {
                    log.info("Board not found: boardId={}.", id);
                    return ResponseUtil.jsonResponse(404, Map.of("error", "Board not found"));
                });
    }

    public record BoardResponse(
            UUID id,
            String title,
            String recipientName,
            ZonedDateTime createdAt,
            boolean canEdit
    ) {
    }

    @Tracing(namespace = "BoardsService")
    public APIGatewayProxyResponseEvent updateBoard(String idStr, String body, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            log.info("Board request rejected: missing or invalid caller identity (ownerId). Client must send a valid JWT.");
            return ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized"));
        }
        if (body == null || body.isBlank()) {
            log.info("Board create/update rejected: request body required.");
            return ResponseUtil.jsonResponse(400, Map.of("error", "Request body required"));
        }

        UUID id;
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            log.info("Invalid board ID format: '{}'. Board ID must be a valid UUID.", idStr);
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid UUID"));
        }

        return boardRepository.findById(id)
                .map(existing -> {
                    if (!ownerId.equals(existing.getOwnerId())) {
                        log.info("Board access forbidden: caller is not the board owner. boardId={}.", id);
                        return ResponseUtil.jsonResponse(403, Map.of("error", "Forbidden"));
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> json = GSON.fromJson(body, Map.class);
                    String title = (String) json.get("title");
                    String recipientName = (String) json.get("recipientName");
                    if (title == null || title.isBlank() || recipientName == null || recipientName.isBlank()) {
                        log.info("Board create/update rejected: title and recipientName are required.");
                        return ResponseUtil.jsonResponse(400, Map.of("error", "title and recipientName required"));
                    }
                    if (title.length() > ValidationConstants.MAX_SHORT_TEXT_LENGTH) {
                        log.info("Board rejected: title or recipientName exceeds max length. Max is {} characters.", ValidationConstants.MAX_SHORT_TEXT_LENGTH);
                        return ResponseUtil.jsonResponse(400, Map.of("error", "title must be at most " + ValidationConstants.MAX_SHORT_TEXT_LENGTH + " characters"));
                    }
                    if (recipientName.length() > ValidationConstants.MAX_SHORT_TEXT_LENGTH) {
                        log.info("Board rejected: title or recipientName exceeds max length. Max is {} characters.", ValidationConstants.MAX_SHORT_TEXT_LENGTH);
                        return ResponseUtil.jsonResponse(400, Map.of("error", "recipientName must be at most " + ValidationConstants.MAX_SHORT_TEXT_LENGTH + " characters"));
                    }

                    try {
                        Board updated = boardRepository.updateBoard(id, ownerId, title, recipientName);
                        return ResponseUtil.jsonResponse(200, updated);
                    } catch (ConditionalCheckFailedException e) {
                        log.warn("Board update conflict: board was modified by another request. boardId={}. Retry with latest data.", id);
                        return ResponseUtil.jsonResponse(403, Map.of("error", "Forbidden"));
                    }
                })
                .orElseGet(() -> {
                    log.info("Board not found: boardId={}.", id);
                    return ResponseUtil.jsonResponse(404, Map.of("error", "Board not found"));
                });
    }

    @Tracing(namespace = "BoardsService")
    public APIGatewayProxyResponseEvent deleteBoard(String idStr, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            log.info("Board request rejected: missing or invalid caller identity (ownerId). Client must send a valid JWT.");
            return ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized"));
        }

        UUID id;
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            log.info("Invalid board ID format: '{}'. Board ID must be a valid UUID.", idStr);
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid UUID"));
        }

        return boardRepository.findById(id)
                .map(existing -> {
                    if (!ownerId.equals(existing.getOwnerId())) {
                        log.info("Board access forbidden: caller is not the board owner. boardId={}.", id);
                        return ResponseUtil.jsonResponse(403, Map.of("error", "Forbidden"));
                    }
                    try {
                        boardRepository.deleteBoard(id, ownerId);
                        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
                        response.setStatusCode(204);
                        response.setHeaders(Map.of("Access-Control-Allow-Origin", "*"));
                        response.setBody(null);
                        return response;
                    } catch (ConditionalCheckFailedException e) {
                        log.warn("Board delete conflict: board was modified by another request. boardId={}. Retry with latest data.", id);
                        return ResponseUtil.jsonResponse(403, Map.of("error", "Forbidden"));
                    }
                })
                .orElseGet(() -> {
                    log.info("Board not found: boardId={}.", id);
                    return ResponseUtil.jsonResponse(404, Map.of("error", "Board not found"));
                });
    }

    @Tracing(namespace = "BoardsService")
    public APIGatewayProxyResponseEvent downloadBoardPdf(String idStr, String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            log.info("Board request rejected: missing or invalid caller identity (ownerId). Client must send a valid JWT.");
            return ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized"));
        }
        // Log when posts table is not configured (postRepository null). RenderConfig issues are logged in BoardPdfRenderer.
        if (postRepository == null) {
            log.error("PDF download not available: POSTS_TABLE not configured. Configure posts table for PDF export.");
            return ResponseUtil.jsonResponse(500, Map.of("error", "PDF download is not configured"));
        }

        UUID id;
        try {
            id = UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            log.info("Invalid board ID format: '{}'. Board ID must be a valid UUID.", idStr);
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid UUID"));
        }

        return boardRepository.findById(id)
                .map(existing -> {
                    if (!ownerId.equals(existing.getOwnerId())) {
                        log.info("Board access forbidden: caller is not the board owner. boardId={}.", id);
                        return ResponseUtil.jsonResponse(403, Map.of("error", "Forbidden"));
                    }

                    List<Post> posts = postRepository.findByBoardIdOrderByCreatedAtAsc(id);
                    byte[] pdfBytes = BoardPdfRenderer.render(existing, posts, pdfRenderConfig);
                    String filename = BoardPdfRenderer.toFilename(existing);

                    APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
                    response.setStatusCode(200);
                    response.setHeaders(Map.of(
                            "Content-Type", "application/pdf",
                            "Content-Disposition", "attachment; filename=\"" + filename + "\"",
                            "Access-Control-Allow-Origin", "*"
                    ));
                    response.setBody(Base64.getEncoder().encodeToString(pdfBytes));
                    response.setIsBase64Encoded(true);
                    return response;
                })
                .orElseGet(() -> {
                    log.info("Board not found: boardId={}.", id);
                    return ResponseUtil.jsonResponse(404, Map.of("error", "Board not found"));
                });
    }

    private static int parseMaxBoardsPerOwner(String value) {
        if (value == null || value.isBlank()) {
            return 100;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 1 ? parsed : 100;
        } catch (NumberFormatException e) {
            return 100;
        }
    }
}
