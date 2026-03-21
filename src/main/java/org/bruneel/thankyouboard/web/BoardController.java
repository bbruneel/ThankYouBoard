package org.bruneel.thankyouboard.web;

import org.bruneel.thankyouboard.domain.Board;
import org.bruneel.thankyouboard.domain.Post;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.bruneel.thankyouboard.service.BoardPdfRenderer;
import org.bruneel.thankyouboard.validation.ImageUrlPolicy;
import org.bruneel.thankyouboard.validation.TimeZoneValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/boards", version = "1")
public class BoardController {

    private static final Logger log = LoggerFactory.getLogger(BoardController.class);

    private final BoardRepository boardRepository;
    private final PostRepository postRepository;
    private final BoardPdfRenderer.RenderConfig pdfRenderConfig;
    private final int maxBoardsPerOwner;

    public BoardController(
            BoardRepository boardRepository,
            PostRepository postRepository,
            @Value("${boards.images.allowed-hosts:" + ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS + "}") String allowedImageHosts,
            @Value("${boards.pdf.image-fetch-timeout:2s}") Duration imageFetchTimeout,
            @Value("${boards.pdf.max-image-bytes-per-image:1048576}") int maxImageBytesPerImage,
            @Value("${boards.pdf.max-image-bytes-total:10485760}") int maxImageBytesTotal,
            @Value("${boards.max-boards-per-owner:100}") int maxBoardsPerOwner) {
        this.boardRepository = boardRepository;
        this.postRepository = postRepository;
        this.pdfRenderConfig = new BoardPdfRenderer.RenderConfig(
                ImageUrlPolicy.fromCsv(allowedImageHosts),
                imageFetchTimeout,
                maxImageBytesPerImage,
                maxImageBytesTotal
        );
        if (maxBoardsPerOwner < 1) {
            throw new IllegalArgumentException("boards.max-boards-per-owner must be >= 1");
        }
        this.maxBoardsPerOwner = maxBoardsPerOwner;
    }

    @PostMapping
    public ResponseEntity<?> createBoard(@RequestBody Board board, Authentication authentication) {
        String ownerId = requireSub(authentication);
        log.info("Creating board '{}'", board.getTitle());
        board.setOwnerId(ownerId);

        long currentBoards = boardRepository.countByOwnerId(ownerId);
        if (currentBoards >= maxBoardsPerOwner) {
            log.warn(
                    "Rejected board creation: ownerId={} boardCount={} limit={}",
                    ownerId, currentBoards, maxBoardsPerOwner
            );
            return ResponseEntity.status(409).body(Map.of(
                    "error", "Board limit reached (" + maxBoardsPerOwner + ")."
            ));
        }

        Board saved = boardRepository.save(board);
        log.debug("Board created with id {}", saved.getId());
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public List<Board> getAllBoards(Authentication authentication) {
        String ownerId = requireSub(authentication);
        log.debug("Fetching all boards for owner {}", ownerId);
        List<Board> boards = boardRepository.findByOwnerIdOrderByCreatedAtAsc(ownerId);
        log.info("Returning {} board(s)", boards.size());
        return boards;
    }

    @GetMapping("/{id}")
    public ResponseEntity<BoardResponse> getBoardById(@PathVariable UUID id, Authentication authentication) {
        log.debug("Looking up board {}", id);
        return boardRepository.findById(id)
                .map(board -> {
                    boolean canEdit = false;
                    if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                        String sub = jwtAuth.getToken().getSubject();
                        canEdit = sub != null && !sub.isBlank() && sub.equals(board.getOwnerId());
                    }
                    log.info("Board {} found: '{}' (canEdit={})", id, board.getTitle(), canEdit);
                    BoardResponse response = new BoardResponse(
                            board.getId(),
                            board.getTitle(),
                            board.getRecipientName(),
                            board.getCreatedAt(),
                            canEdit
                    );
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    log.info("Board {} not found", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PutMapping("/{id}")
    public ResponseEntity<Board> updateBoard(@PathVariable UUID id,
                                             @RequestBody Board updated,
                                             Authentication authentication) {
        String ownerId = requireSub(authentication);
        log.debug("Updating board {}", id);

        var existingOpt = boardRepository.findById(id);
        if (existingOpt.isEmpty()) {
            log.info("Board {} not found for update", id);
            return ResponseEntity.notFound().build();
        }
        Board existing = existingOpt.get();
        if (!ownerId.equals(existing.getOwnerId())) {
            log.warn("User {} attempted to update board {} they do not own", ownerId, id);
            return ResponseEntity.status(403).build();
        }

        if (updated.getTitle() != null) {
            existing.setTitle(updated.getTitle());
        }
        if (updated.getRecipientName() != null) {
            existing.setRecipientName(updated.getRecipientName());
        }

        Board saved = boardRepository.save(existing);
        log.info("Board {} updated", id);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBoard(@PathVariable UUID id, Authentication authentication) {
        String ownerId = requireSub(authentication);
        log.debug("Deleting board {}", id);

        var existingOpt = boardRepository.findById(id);
        if (existingOpt.isEmpty()) {
            log.info("Board {} not found for delete", id);
            return ResponseEntity.notFound().build();
        }
        Board existing = existingOpt.get();
        if (!ownerId.equals(existing.getOwnerId())) {
            log.warn("User {} attempted to delete board {} they do not own", ownerId, id);
            return ResponseEntity.status(403).build();
        }
        boardRepository.delete(existing);
        log.info("Board {} deleted by owner {}", id, ownerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadBoardPdf(
            @PathVariable UUID id,
            @RequestHeader(value = "Time-Zone", required = false) String timeZoneHeader,
            Authentication authentication) {
        String ownerId = requireSub(authentication);
        log.debug("Generating PDF export for board {}", id);

        var boardOpt = boardRepository.findById(id);
        if (boardOpt.isEmpty()) {
            log.info("Board {} not found for PDF download", id);
            return ResponseEntity.notFound().build();
        }

        Board board = boardOpt.get();
        if (!ownerId.equals(board.getOwnerId())) {
            log.warn("User {} attempted to download PDF for board {} they do not own", ownerId, id);
            return ResponseEntity.status(403).build();
        }

        ZoneId displayZone = parseTimeZoneHeader(timeZoneHeader);
        List<Post> posts = postRepository.findByBoardIdOrderByCreatedAtAsc(id);
        byte[] pdf = BoardPdfRenderer.render(board, posts, pdfRenderConfig, displayZone);
        String filename = BoardPdfRenderer.toFilename(board);
        log.info("Generated PDF export for board {} with {} post(s)", id, posts.size());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private static ZoneId parseTimeZoneHeader(String value) {
        return TimeZoneValidator.parse(value);
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

    public record BoardResponse(
            UUID id,
            String title,
            String recipientName,
            ZonedDateTime createdAt,
            boolean canEdit
    ) {
    }
}
