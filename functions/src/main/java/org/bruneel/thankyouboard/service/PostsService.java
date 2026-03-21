package org.bruneel.thankyouboard.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bruneel.thankyouboard.handler.ResponseUtil;
import org.bruneel.thankyouboard.handler.ZonedDateTimeAdapter;
import org.bruneel.thankyouboard.model.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.lambda.powertools.tracing.Tracing;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PostsService {

    private static final Logger log = LoggerFactory.getLogger(PostsService.class);
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
            .create();

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final int maxPostsPerBoard;
    private final ImageUrlPolicy giphyUrlPolicy;
    private final ImageUrlPolicy uploadedImageUrlPolicy;
    private final CapabilityTokenService capabilityTokenService;

    public PostsService(String postsTable, String boardsTable) {
        this.postRepository = new PostRepository(postsTable);
        this.boardRepository = new BoardRepository(boardsTable);
        this.maxPostsPerBoard = parseMaxPostsPerBoard(System.getenv("MAX_POSTS_PER_BOARD"));
        this.giphyUrlPolicy = ImageUrlPolicy.fromCsv(System.getenv("BOARDS_IMAGES_ALLOWED_HOSTS"));
        this.uploadedImageUrlPolicy = ImageUrlPolicy.fromCsv(System.getenv("BOARDS_UPLOADED_IMAGES_ALLOWED_HOSTS"));
        this.capabilityTokenService = new CapabilityTokenService(
                parseIntOrDefault(System.getenv("CAPABILITY_TOKEN_RANDOM_BYTES"), 32),
                parseLongOrDefault(System.getenv("POSTS_CAPABILITY_TOKEN_EXPIRY_HOURS"), 24)
        );
    }

    PostsService(PostRepository postRepository, BoardRepository boardRepository, int maxPostsPerBoard) {
        this(postRepository, boardRepository, maxPostsPerBoard, ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS, "");
    }

    PostsService(PostRepository postRepository, BoardRepository boardRepository, int maxPostsPerBoard, String allowedImageHosts, String allowedUploadedImageHosts) {
        this.postRepository = postRepository;
        this.boardRepository = boardRepository;
        if (maxPostsPerBoard < 1) {
            throw new IllegalArgumentException("MAX_POSTS_PER_BOARD must be >= 1");
        }
        this.maxPostsPerBoard = maxPostsPerBoard;
        this.giphyUrlPolicy = ImageUrlPolicy.fromCsv(allowedImageHosts);
        this.uploadedImageUrlPolicy = ImageUrlPolicy.fromCsv(allowedUploadedImageHosts);
        this.capabilityTokenService = new CapabilityTokenService(32, 24);
    }

    @Tracing(namespace = "PostsService")
    public APIGatewayProxyResponseEvent createPost(String boardIdStr, String body) {
        if (boardIdStr == null || boardIdStr.isBlank()) {
            log.info("Post rejected: boardId required (from path).");
            return ResponseUtil.jsonResponse(400, Map.of("error", "boardId required (from path)"));
        }
        if (body == null || body.isBlank()) {
            log.info("Post rejected: request body required.");
            return ResponseUtil.jsonResponse(400, Map.of("error", "Request body required"));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = GSON.fromJson(body, Map.class);
            String authorName = (String) json.get("authorName");
            String messageText = json.containsKey("messageText") ? (String) json.get("messageText") : null;
            String giphyUrl = json.containsKey("giphyUrl") ? (String) json.get("giphyUrl") : null;
            String uploadedImageUrl = json.containsKey("uploadedImageUrl") ? (String) json.get("uploadedImageUrl") : null;
            if (authorName == null || authorName.isBlank()) {
                log.info("Post rejected: authorName required.");
                return ResponseUtil.jsonResponse(400, Map.of("error", "authorName required"));
            }
            if (authorName.length() > ValidationConstants.MAX_SHORT_TEXT_LENGTH) {
                log.info("Post rejected: authorName exceeds max length. Max is {} characters.", ValidationConstants.MAX_SHORT_TEXT_LENGTH);
                return ResponseUtil.jsonResponse(400, Map.of("error", "authorName must be at most " + ValidationConstants.MAX_SHORT_TEXT_LENGTH + " characters"));
            }
            if (messageText != null && messageText.length() > ValidationConstants.MAX_LONG_TEXT_LENGTH) {
                log.info("Post rejected: messageText exceeds max length. Max is {} characters.", ValidationConstants.MAX_LONG_TEXT_LENGTH);
                return ResponseUtil.jsonResponse(400, Map.of("error", "messageText must be at most " + ValidationConstants.MAX_LONG_TEXT_LENGTH + " characters"));
            }
            if (giphyUrl != null && giphyUrl.length() > ValidationConstants.MAX_LONG_TEXT_LENGTH) {
                log.info("Post rejected: giphyUrl exceeds max length. Max is {} characters.", ValidationConstants.MAX_LONG_TEXT_LENGTH);
                return ResponseUtil.jsonResponse(400, Map.of("error", "giphyUrl must be at most " + ValidationConstants.MAX_LONG_TEXT_LENGTH + " characters"));
            }
            if (uploadedImageUrl != null && uploadedImageUrl.length() > ValidationConstants.MAX_LONG_TEXT_LENGTH) {
                log.info("Post rejected: uploadedImageUrl exceeds max length. Max is {} characters.", ValidationConstants.MAX_LONG_TEXT_LENGTH);
                return ResponseUtil.jsonResponse(400, Map.of("error", "uploadedImageUrl must be at most " + ValidationConstants.MAX_LONG_TEXT_LENGTH + " characters"));
            }

            boolean hasGiphy = giphyUrl != null && !giphyUrl.isBlank();
            boolean hasUpload = uploadedImageUrl != null && !uploadedImageUrl.isBlank();
            if (hasGiphy && hasUpload) {
                log.info("Post rejected: both giphyUrl and uploadedImageUrl provided; only one allowed.");
                return ResponseUtil.jsonResponse(400, Map.of("error", "At most one of giphyUrl or uploadedImageUrl may be set"));
            }
            if (giphyUrl != null && !giphyUrl.isBlank()) {
                String normalizedUrl = giphyUrlPolicy.normalizeAndValidateHttpsUrl(giphyUrl);
                if (normalizedUrl == null) {
                    log.warn("Post rejected: giphyUrl host not allowed or not https. url={}. Use an https URL from an allowed host (e.g. giphy.com).", giphyUrl);
                    return ResponseUtil.jsonResponse(400, Map.of("error", "giphyUrl must be an https URL from an allowed host"));
                }
                giphyUrl = normalizedUrl;
            }
            if (uploadedImageUrl != null && !uploadedImageUrl.isBlank()) {
                String normalizedUrl = uploadedImageUrlPolicy.normalizeAndValidateHttpOrHttpsUrl(uploadedImageUrl);
                if (normalizedUrl == null) {
                    log.warn("Post rejected: uploadedImageUrl host not allowed or not http(s). url={}. Use an http(s) URL from an allowed host.", uploadedImageUrl);
                    return ResponseUtil.jsonResponse(400, Map.of("error", "uploadedImageUrl must be an http(s) URL from an allowed host"));
                }
                uploadedImageUrl = normalizedUrl;
            }
            UUID boardId = UUID.fromString(boardIdStr);
            if (boardRepository.findById(boardId).isEmpty()) {
                log.info("Post rejected: board not found. boardId={}.", boardIdStr);
                return ResponseUtil.jsonResponse(404, Map.of("error", "Board not found"));
            }
            long currentPosts = postRepository.countByBoardId(boardId);
            if (currentPosts >= maxPostsPerBoard) {
                log.warn("Post rejected: board post limit reached. boardId={}, limit={}. Remove posts or increase MAX_POSTS_PER_BOARD.", boardIdStr, maxPostsPerBoard);
                return ResponseUtil.jsonResponse(409, Map.of("error", "Board post limit reached (" + maxPostsPerBoard + ")."));
            }
            ZonedDateTime now = ZonedDateTime.now();
            Post post = new Post(UUID.randomUUID(), boardId, authorName, messageText, giphyUrl, uploadedImageUrl, now);

            var issued = capabilityTokenService.issueToken();
            post.setCapabilityTokenHash(issued.tokenHashHex());
            post.setCapabilityTokenExpiresAt(issued.expiresAt());

            postRepository.save(post);
            post.setEditDeleteToken(issued.token());
            return ResponseUtil.jsonResponse(200, post);
        } catch (IllegalArgumentException e) {
            log.info("Post rejected: invalid boardId UUID: '{}'.", boardIdStr);
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid boardId UUID"));
        } catch (Exception e) {
            log.warn("Post rejected: invalid JSON. {}", e.getMessage());
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid JSON: " + e.getMessage()));
        }
    }

    @Tracing(namespace = "PostsService")
    public APIGatewayProxyResponseEvent listPostsByBoard(String boardIdStr) {
        UUID boardId;
        try {
            boardId = UUID.fromString(boardIdStr);
        } catch (IllegalArgumentException e) {
            log.info("Post rejected: invalid boardId UUID: '{}'.", boardIdStr);
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid UUID"));
        }
        List<Post> posts = postRepository.findByBoardIdOrderByCreatedAtAsc(boardId);
        return ResponseUtil.jsonResponse(200, posts);
    }

    @Tracing(namespace = "PostsService")
    public APIGatewayProxyResponseEvent updatePost(String boardIdStr, String postIdStr, String body, String ownerId) {
        return updatePost(boardIdStr, postIdStr, body, ownerId, null);
    }

    @Tracing(namespace = "PostsService")
    public APIGatewayProxyResponseEvent updatePost(
            String boardIdStr,
            String postIdStr,
            String body,
            String ownerId,
            String capabilityToken
    ) {
        if (ownerId == null || ownerId.isBlank()) {
            // Owner can be authorized via capability token (anonymous). Keep JWT-owner logic below.
            // We only return 401 when *both* ownerId and capabilityToken are missing.
            if (capabilityToken == null || capabilityToken.isBlank()) {
                return ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized"));
            }
        }
        UUID boardId;
        UUID postId;
        try {
            boardId = UUID.fromString(boardIdStr);
            postId = UUID.fromString(postIdStr);
        } catch (IllegalArgumentException e) {
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid UUID"));
        }
        if (body == null || body.isBlank()) {
            return ResponseUtil.jsonResponse(400, Map.of("error", "Request body required"));
        }

        Optional<org.bruneel.thankyouboard.model.Board> boardOpt = boardRepository.findById(boardId);
        if (boardOpt.isEmpty()) {
            return ResponseUtil.jsonResponse(404, Map.of("error", "Board not found"));
        }

        Optional<Post> existingOpt = postRepository.findById(postId);
        if (existingOpt.isEmpty() || !boardId.equals(existingOpt.get().getBoardId())) {
            return ResponseUtil.jsonResponse(404, Map.of("error", "Post not found"));
        }

        Post existing = existingOpt.get();

        boolean canUpdateByOwner = ownerId != null && !ownerId.isBlank()
                && ownerId.equals(boardOpt.get().getOwnerId());
        var tokenValidation = capabilityTokenService.validateForPost(capabilityToken, existing);
        boolean canUpdateByCapability = tokenValidation.valid();

        if (!canUpdateByOwner && !canUpdateByCapability) {
            String reason = tokenValidation.reason() != null ? tokenValidation.reason().name() : "UNKNOWN";
            if ((capabilityToken == null || capabilityToken.isBlank()) && (ownerId == null || ownerId.isBlank())) {
                log.warn("Unauthorized post update: missing ownerId and capabilityToken. boardId={} postId={}", boardIdStr, postIdStr);
                return ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized"));
            }
            log.warn(
                    "Forbidden post update attempt: ownerIdPresent={} capabilityTokenReason={} boardId={} postId={}",
                    ownerId != null && !ownerId.isBlank(),
                    reason,
                    boardIdStr,
                    postIdStr
            );
            return ResponseUtil.jsonResponse(403, Map.of("error", "Forbidden"));
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = GSON.fromJson(body, Map.class);

            boolean hasAuthor = json.containsKey("authorName");
            boolean hasMessage = json.containsKey("messageText");
            boolean hasGiphy = json.containsKey("giphyUrl");
            boolean hasUpload = json.containsKey("uploadedImageUrl");

            if (!hasAuthor && !hasMessage && !hasGiphy && !hasUpload) {
                return ResponseUtil.jsonResponse(400, Map.of("error", "No fields to update"));
            }

            String authorName = hasAuthor ? (String) json.get("authorName") : null;
            String messageText = hasMessage ? (String) json.get("messageText") : null;
            String giphyUrl = hasGiphy ? (String) json.get("giphyUrl") : null;
            String uploadedImageUrl = hasUpload ? (String) json.get("uploadedImageUrl") : null;

            boolean giphySet = giphyUrl != null && !giphyUrl.isBlank();
            boolean uploadSet = uploadedImageUrl != null && !uploadedImageUrl.isBlank();
            if (giphySet && uploadSet) {
                return ResponseUtil.jsonResponse(400, Map.of("error", "At most one of giphyUrl or uploadedImageUrl may be set"));
            }

            if (hasAuthor) {
                if (authorName == null || authorName.isBlank()) {
                    return ResponseUtil.jsonResponse(400, Map.of("error", "authorName required"));
                }
                if (authorName.length() > ValidationConstants.MAX_SHORT_TEXT_LENGTH) {
                    return ResponseUtil.jsonResponse(400, Map.of("error", "authorName must be at most " + ValidationConstants.MAX_SHORT_TEXT_LENGTH + " characters"));
                }
            }
            if (hasMessage && messageText != null && messageText.length() > ValidationConstants.MAX_LONG_TEXT_LENGTH) {
                return ResponseUtil.jsonResponse(400, Map.of("error", "messageText must be at most " + ValidationConstants.MAX_LONG_TEXT_LENGTH + " characters"));
            }
            if (hasGiphy && giphyUrl != null && !giphyUrl.isBlank()) {
                String normalizedUrl = giphyUrlPolicy.normalizeAndValidateHttpsUrl(giphyUrl);
                if (normalizedUrl == null) {
                    return ResponseUtil.jsonResponse(400, Map.of("error", "giphyUrl must be an https URL from an allowed host"));
                }
                giphyUrl = normalizedUrl;
            } else if (hasGiphy) {
                giphyUrl = null;
            }
            if (hasUpload && uploadedImageUrl != null && !uploadedImageUrl.isBlank()) {
                String normalizedUrl = uploadedImageUrlPolicy.normalizeAndValidateHttpOrHttpsUrl(uploadedImageUrl);
                if (normalizedUrl == null) {
                    return ResponseUtil.jsonResponse(400, Map.of("error", "uploadedImageUrl must be an http(s) URL from an allowed host"));
                }
                uploadedImageUrl = normalizedUrl;
            } else if (hasUpload) {
                uploadedImageUrl = null;
            }

            Post updated = postRepository.updatePost(
                    existing,
                    hasAuthor, authorName,
                    hasMessage, messageText,
                    hasGiphy, giphyUrl,
                    hasUpload, uploadedImageUrl
            );
            if (canUpdateByOwner) {
                log.info("Post updated boardId={} postId={} by ownerId={}", boardId, postId, ownerId);
            } else {
                log.info("Post updated boardId={} postId={} by anonymous capability", boardId, postId);
            }
            return ResponseUtil.jsonResponse(200, updated);
        } catch (Exception e) {
            log.warn("Post update rejected: invalid JSON. {}", e.getMessage());
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid JSON: " + e.getMessage()));
        }
    }

    @Tracing(namespace = "PostsService")
    public APIGatewayProxyResponseEvent deletePost(String boardIdStr, String postIdStr, String ownerId) {
        return deletePost(boardIdStr, postIdStr, ownerId, null);
    }

    @Tracing(namespace = "PostsService")
    public APIGatewayProxyResponseEvent deletePost(
            String boardIdStr,
            String postIdStr,
            String ownerId,
            String capabilityToken
    ) {
        if (ownerId == null || ownerId.isBlank()) {
            if (capabilityToken == null || capabilityToken.isBlank()) {
                return ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized"));
            }
        }
        UUID boardId;
        UUID postId;
        try {
            boardId = UUID.fromString(boardIdStr);
            postId = UUID.fromString(postIdStr);
        } catch (IllegalArgumentException e) {
            return ResponseUtil.jsonResponse(400, Map.of("error", "Invalid UUID"));
        }

        Optional<org.bruneel.thankyouboard.model.Board> boardOpt = boardRepository.findById(boardId);
        if (boardOpt.isEmpty()) {
            return ResponseUtil.jsonResponse(404, Map.of("error", "Board not found"));
        }

        Optional<Post> existingOpt = postRepository.findById(postId);
        if (existingOpt.isEmpty() || !boardId.equals(existingOpt.get().getBoardId())) {
            return ResponseUtil.jsonResponse(404, Map.of("error", "Post not found"));
        }

        Post existing = existingOpt.get();

        boolean canDeleteByOwner = ownerId != null && !ownerId.isBlank()
                && ownerId.equals(boardOpt.get().getOwnerId());
        var tokenValidation = capabilityTokenService.validateForPost(capabilityToken, existing);
        boolean canDeleteByCapability = tokenValidation.valid();

        if (!canDeleteByOwner && !canDeleteByCapability) {
            if ((capabilityToken == null || capabilityToken.isBlank()) && (ownerId == null || ownerId.isBlank())) {
                log.warn("Unauthorized post delete: missing ownerId and capabilityToken. boardId={} postId={}", boardIdStr, postIdStr);
                return ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized"));
            }
            String reason = tokenValidation.reason() != null ? tokenValidation.reason().name() : "UNKNOWN";
            log.warn(
                    "Forbidden post delete attempt: ownerIdPresent={} capabilityTokenReason={} boardId={} postId={}",
                    ownerId != null && !ownerId.isBlank(),
                    reason,
                    boardIdStr,
                    postIdStr
            );
            return ResponseUtil.jsonResponse(403, Map.of("error", "Forbidden"));
        }

        postRepository.delete(existing);
        if (canDeleteByOwner) {
            log.info("Post deleted boardId={} postId={} by ownerId={}", boardId, postId, ownerId);
        } else {
            log.info("Post deleted boardId={} postId={} by anonymous capability", boardId, postId);
        }
        return ResponseUtil.noContentResponse();
    }

    private static int parseMaxPostsPerBoard(String value) {
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

    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseLongOrDefault(String value, long defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
