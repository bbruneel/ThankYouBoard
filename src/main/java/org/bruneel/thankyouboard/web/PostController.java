package org.bruneel.thankyouboard.web;

import org.bruneel.thankyouboard.domain.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.bruneel.thankyouboard.service.CapabilityTokenService;
import org.bruneel.thankyouboard.validation.ImageUrlPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/boards/{boardId}/posts", version = "1")
public class PostController {

    private static final Logger log = LoggerFactory.getLogger(PostController.class);
    public static final String POST_EDIT_DELETE_CAPABILITY_HEADER = "X-Post-Capability-Token";

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final CapabilityTokenService capabilityTokenService;
    private final int maxPostsPerBoard;
    private final ImageUrlPolicy giphyUrlPolicy;
    private final ImageUrlPolicy uploadedImageUrlPolicy;

    public PostController(PostRepository postRepository,
                          BoardRepository boardRepository,
                          CapabilityTokenService capabilityTokenService,
                          @Value("${boards.max-posts-per-board:100}") int maxPostsPerBoard,
                          @Value("${boards.images.allowed-hosts:" + ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS + "}") String allowedImageHosts,
                          @Value("${boards.uploaded-images.allowed-hosts:localhost,127.0.0.1}") String allowedUploadedImageHosts) {
        this.postRepository = postRepository;
        this.boardRepository = boardRepository;
        this.capabilityTokenService = capabilityTokenService;
        if (maxPostsPerBoard < 1) {
            throw new IllegalArgumentException("boards.max-posts-per-board must be >= 1");
        }
        this.maxPostsPerBoard = maxPostsPerBoard;
        this.giphyUrlPolicy = ImageUrlPolicy.fromCsv(allowedImageHosts);
        this.uploadedImageUrlPolicy = ImageUrlPolicy.fromCsv(allowedUploadedImageHosts);
    }

    @PostMapping
    public ResponseEntity<?> createPost(@PathVariable UUID boardId, @RequestBody Map<String, Object> body) {
        String authorName = (String) body.get("authorName");
        String messageText = body.containsKey("messageText") ? (String) body.get("messageText") : null;
        String giphyUrl = body.containsKey("giphyUrl") ? (String) body.get("giphyUrl") : null;
        String uploadedImageUrl = body.containsKey("uploadedImageUrl") ? (String) body.get("uploadedImageUrl") : null;

        if (authorName == null || authorName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "authorName required"));
        }
        if (authorName.length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "authorName must be at most 200 characters"));
        }
        if (messageText != null && messageText.length() > 10_000) {
            return ResponseEntity.badRequest().body(Map.of("error", "messageText must be at most 10000 characters"));
        }
        if (giphyUrl != null && giphyUrl.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "giphyUrl must be at most 1000 characters"));
        }
        if (uploadedImageUrl != null && uploadedImageUrl.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "uploadedImageUrl must be at most 1000 characters"));
        }

        boolean hasGiphy = giphyUrl != null && !giphyUrl.isBlank();
        boolean hasUpload = uploadedImageUrl != null && !uploadedImageUrl.isBlank();
        if (hasGiphy && hasUpload) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "At most one of giphyUrl or uploadedImageUrl may be set"
            ));
        }

        if (giphyUrl != null && !giphyUrl.isBlank()) {
            String normalizedUrl = giphyUrlPolicy.normalizeAndValidateHttpsUrl(giphyUrl);
            if (normalizedUrl == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "giphyUrl must be an https URL from an allowed host"
                ));
            }
            giphyUrl = normalizedUrl;
        }
        if (uploadedImageUrl != null && !uploadedImageUrl.isBlank()) {
            String normalizedUrl = uploadedImageUrlPolicy.normalizeAndValidateHttpOrHttpsUrl(uploadedImageUrl);
            if (normalizedUrl == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "uploadedImageUrl must be an http(s) URL from an allowed host"
                ));
            }
            uploadedImageUrl = normalizedUrl;
        }
        long currentPosts = postRepository.countByBoardId(boardId);
        if (currentPosts >= maxPostsPerBoard) {
            log.warn("Rejected post for board {}: postCount={} limit={}", boardId, currentPosts, maxPostsPerBoard);
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Board post limit reached (" + maxPostsPerBoard + ")."
            ));
        }
        log.info("Creating post for board {} by author '{}'", boardId, authorName);
        Post post = new Post();
        post.setBoardId(boardId);
        post.setAuthorName(authorName);
        post.setMessageText(messageText);
        post.setGiphyUrl(giphyUrl);
        post.setUploadedImageUrl(uploadedImageUrl);

        var issued = capabilityTokenService.issueToken();
        post.setCapabilityTokenHash(issued.tokenHashHex());
        post.setCapabilityTokenExpiresAt(issued.expiresAt());

        Post saved = postRepository.save(post);
        log.debug("Post created with id {}", saved.getId());
        saved.setEditDeleteToken(issued.token());
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public List<Post> getPostsByBoardId(@PathVariable UUID boardId) {
        log.debug("Fetching posts for board {}", boardId);
        List<Post> posts = postRepository.findByBoardIdOrderByCreatedAtAsc(boardId);
        log.info("Returning {} post(s) for board {}", posts.size(), boardId);
        return posts;
    }

    @PutMapping("/{postId}")
    public ResponseEntity<?> updatePost(
            @PathVariable UUID boardId,
            @PathVariable UUID postId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = POST_EDIT_DELETE_CAPABILITY_HEADER, required = false) String capabilityToken,
            Authentication authentication
    ) {
        boolean hasJwt = authentication instanceof JwtAuthenticationToken;
        if (!hasJwt && (capabilityToken == null || capabilityToken.isBlank())) {
            return ResponseEntity.status(401).build();
        }
        var boardOpt = boardRepository.findById(boardId);
        if (boardOpt.isEmpty()) {
            log.info("Board {} not found for post update {}", boardId, postId);
            return ResponseEntity.notFound().build();
        }

        var existingOpt = postRepository.findById(postId);
        if (existingOpt.isEmpty() || !boardId.equals(existingOpt.get().getBoardId())) {
            log.info("Post {} not found for update on board {}", postId, boardId);
            return ResponseEntity.notFound().build();
        }

        Post post = existingOpt.get();

        boolean canEditByOwner = false;
        String ownerId = null;
        if (authentication instanceof JwtAuthenticationToken) {
            ownerId = requireSub(authentication);
            canEditByOwner = ownerId.equals(boardOpt.get().getOwnerId());
        }

        var tokenValidation = capabilityTokenService.validateForPost(capabilityToken, post);
        boolean canEditByCapability = tokenValidation.valid();

        if (!canEditByOwner && !canEditByCapability) {
            String hashPrefix = post.getCapabilityTokenHash() != null && post.getCapabilityTokenHash().length() >= 8
                    ? post.getCapabilityTokenHash().substring(0, 8)
                    : "none";
            log.warn(
                    "Unauthorized post update attempt for post {} on board {}. hasJwt={} tokenReason={} tokenHashPrefix={}",
                    postId,
                    boardId,
                    hasJwt,
                    tokenValidation.reason(),
                    hashPrefix
            );

            if (!hasJwt && tokenValidation.reason() == CapabilityTokenService.CapabilityTokenFailureReason.MISSING_TOKEN) {
                return ResponseEntity.status(401).build();
            }
            return ResponseEntity.status(403).build();
        }
        String authorName = body.containsKey("authorName") ? (String) body.get("authorName") : null;
        String messageText = body.containsKey("messageText") ? (String) body.get("messageText") : null;
        String giphyUrl = body.containsKey("giphyUrl") ? (String) body.get("giphyUrl") : null;
        String uploadedImageUrl = body.containsKey("uploadedImageUrl") ? (String) body.get("uploadedImageUrl") : null;

        if (body.containsKey("authorName") && authorName != null && authorName.length() > 200) {
            return ResponseEntity.badRequest().body(Map.of("error", "authorName must be at most 200 characters"));
        }
        if (body.containsKey("messageText") && messageText != null && messageText.length() > 10_000) {
            return ResponseEntity.badRequest().body(Map.of("error", "messageText must be at most 10000 characters"));
        }
        if (body.containsKey("giphyUrl") && giphyUrl != null && giphyUrl.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "giphyUrl must be at most 1000 characters"));
        }
        if (body.containsKey("uploadedImageUrl") && uploadedImageUrl != null && uploadedImageUrl.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "uploadedImageUrl must be at most 1000 characters"));
        }

        boolean hasGiphy = giphyUrl != null && !giphyUrl.isBlank();
        boolean hasUpload = uploadedImageUrl != null && !uploadedImageUrl.isBlank();
        if (hasGiphy && hasUpload) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "At most one of giphyUrl or uploadedImageUrl may be set"
            ));
        }

        if (body.containsKey("authorName")) {
            if (authorName == null || authorName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "authorName required"));
            }
            post.setAuthorName(authorName);
        }
        if (body.containsKey("messageText")) {
            post.setMessageText(messageText);
        }
        if (body.containsKey("giphyUrl")) {
            if (giphyUrl == null || giphyUrl.isBlank()) {
                post.setGiphyUrl(null);
            } else {
                String normalizedUrl = giphyUrlPolicy.normalizeAndValidateHttpsUrl(giphyUrl);
                if (normalizedUrl == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "giphyUrl must be an https URL from an allowed host"
                    ));
                }
                post.setGiphyUrl(normalizedUrl);
            }
            if (hasGiphy) {
                post.setUploadedImageUrl(null);
            }
        }
        if (body.containsKey("uploadedImageUrl")) {
            if (uploadedImageUrl == null || uploadedImageUrl.isBlank()) {
                post.setUploadedImageUrl(null);
            } else {
                String normalizedUrl = uploadedImageUrlPolicy.normalizeAndValidateHttpOrHttpsUrl(uploadedImageUrl);
                if (normalizedUrl == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "uploadedImageUrl must be an http(s) URL from an allowed host"
                    ));
                }
                post.setUploadedImageUrl(normalizedUrl);
            }
            if (hasUpload) {
                post.setGiphyUrl(null);
            }
        }

        Post saved = postRepository.save(post);
        if (canEditByOwner) {
            log.info("Post {} updated by board owner {} on board {}", postId, ownerId, boardId);
        } else {
            String hashPrefix = post.getCapabilityTokenHash() != null && post.getCapabilityTokenHash().length() >= 8
                    ? post.getCapabilityTokenHash().substring(0, 8)
                    : "none";
            log.info("Post {} updated by anonymous capability on board {} tokenHashPrefix={}", postId, boardId, hashPrefix);
        }
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID boardId,
            @PathVariable UUID postId,
            @RequestHeader(value = POST_EDIT_DELETE_CAPABILITY_HEADER, required = false) String capabilityToken,
            Authentication authentication
    ) {
        boolean hasJwt = authentication instanceof JwtAuthenticationToken;
        if (!hasJwt && (capabilityToken == null || capabilityToken.isBlank())) {
            return ResponseEntity.status(401).build();
        }
        var boardOpt = boardRepository.findById(boardId);
        if (boardOpt.isEmpty()) {
            log.info("Board {} not found for post delete {}", boardId, postId);
            return ResponseEntity.notFound().build();
        }

        var existingOpt = postRepository.findById(postId);
        if (existingOpt.isEmpty() || !boardId.equals(existingOpt.get().getBoardId())) {
            log.info("Post {} not found for delete on board {}", postId, boardId);
            return ResponseEntity.notFound().build();
        }

        Post post = existingOpt.get();

        boolean canDeleteByOwner = false;
        String ownerId = null;
        if (authentication instanceof JwtAuthenticationToken) {
            ownerId = requireSub(authentication);
            canDeleteByOwner = ownerId.equals(boardOpt.get().getOwnerId());
        }

        var tokenValidation = capabilityTokenService.validateForPost(capabilityToken, post);
        boolean canDeleteByCapability = tokenValidation.valid();

        if (!canDeleteByOwner && !canDeleteByCapability) {
            String hashPrefix = post.getCapabilityTokenHash() != null && post.getCapabilityTokenHash().length() >= 8
                    ? post.getCapabilityTokenHash().substring(0, 8)
                    : "none";
            log.warn(
                    "Unauthorized post delete attempt for post {} on board {}. hasJwt={} tokenReason={} tokenHashPrefix={}",
                    postId,
                    boardId,
                    hasJwt,
                    tokenValidation.reason(),
                    hashPrefix
            );

            if (!hasJwt && tokenValidation.reason() == CapabilityTokenService.CapabilityTokenFailureReason.MISSING_TOKEN) {
                return ResponseEntity.status(401).build();
            }
            return ResponseEntity.status(403).build();
        }

        postRepository.delete(post);
        if (canDeleteByOwner) {
            log.info("Post {} deleted by board owner {} on board {}", postId, ownerId, boardId);
        } else {
            String hashPrefix = post.getCapabilityTokenHash() != null && post.getCapabilityTokenHash().length() >= 8
                    ? post.getCapabilityTokenHash().substring(0, 8)
                    : "none";
            log.info("Post {} deleted by anonymous capability on board {} tokenHashPrefix={}", postId, boardId, hashPrefix);
        }
        return ResponseEntity.noContent().build();
    }

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
