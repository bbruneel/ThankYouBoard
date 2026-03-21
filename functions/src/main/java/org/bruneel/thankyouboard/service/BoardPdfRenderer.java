package org.bruneel.thankyouboard.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.bruneel.thankyouboard.model.Board;
import org.bruneel.thankyouboard.model.Post;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class BoardPdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(BoardPdfRenderer.class);

    private static final PDRectangle PAGE_SIZE = PDRectangle.LETTER;
    private static final float PAGE_MARGIN = 28f;
    private static final float SECTION_GAP = 18f;
    private static final float GRID_GAP = 16f;
    private static final float CARD_PADDING = 14f;
    private static final float CARD_MIN_HEIGHT = 170f;
    private static final float IMAGE_MIN_HEIGHT = 90f;
    private static final float IMAGE_MAX_HEIGHT = 220f;
    private static final float PLACEHOLDER_IMAGE_HEIGHT = 110f;
    private static final float HEADER_TITLE_SIZE = 21f;
    private static final float HEADER_SUBTITLE_SIZE = 15f;
    private static final float META_TEXT_SIZE = 10f;
    private static final float BODY_TEXT_SIZE = 11f;
    private static final float FOOTER_TEXT_SIZE = 10f;
    private static final float BODY_LINE_HEIGHT = 14f;
    private static final DateTimeFormatter DATE_FORMAT_UTC = DateTimeFormatter.ofPattern("MMMM d, uuuu 'at' h:mm a 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_FORMAT_WITH_ZONE = DateTimeFormatter.ofPattern("MMMM d, uuuu 'at' h:mm a zzz", Locale.ENGLISH);
    private static final String UNICODE_FONT_RESOURCE = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";
    private static final String FALLBACK_FONT_RESOURCE = "/fonts/Symbola.ttf";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private BoardPdfRenderer() {
    }

    static byte[] render(Board board, List<Post> posts) {
        return render(board, posts, RenderConfig.defaults());
    }

    static byte[] render(Board board, List<Post> posts, RenderConfig renderConfig) {
        long startNanos = System.nanoTime();
        String boardId = board.getId() != null ? board.getId().toString() : "null";
        String boardTitle = safe(board.getTitle());
        log.info("Rendering PDF for board {} with {} posts (title='{}')", boardId, posts.size(), boardTitle);
        try (PDDocument document = new PDDocument();
             InputStream fontStream = BoardPdfRenderer.class.getResourceAsStream(UNICODE_FONT_RESOURCE);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (fontStream == null) {
                log.error("Unicode font resource not found at path {}", UNICODE_FONT_RESOURCE);
                throw new IllegalStateException("Unicode font resource not found: " + UNICODE_FONT_RESOURCE);
            }
            PDFont unicodeFont = PDType0Font.load(document, fontStream);
            PDFont fallbackFont = loadFallbackFont(document);

            PdfCanvas canvas = new PdfCanvas(document, unicodeFont, fallbackFont);
            canvas.drawHeader(board);

            if (posts.isEmpty()) {
                log.info("Rendering PDF for board {} with no posts; using empty state layout", boardId);
                canvas.drawEmptyState();
            } else {
                List<PostCardData> cards = new ArrayList<>(posts.size());
                long[] remainingImageBytes = new long[] { renderConfig.maxImageBytesTotal() };
                for (Post post : posts) {
                    cards.add(PostCardData.from(post, document, unicodeFont, fallbackFont, renderConfig, remainingImageBytes));
                }
                log.debug("Prepared {} post cards for board {} (image budget={} bytes, per-image cap={} bytes)",
                        cards.size(), boardId, renderConfig.maxImageBytesTotal(), renderConfig.maxImageBytesPerImage());
                canvas.drawCards(cards);
            }
            canvas.close();
            document.save(output);
            byte[] result = output.toByteArray();
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("Rendered PDF for board {}: {} bytes, {} pages, duration={}ms",
                    boardId, result.length, document.getNumberOfPages(), durationMs);
            return result;
        } catch (IOException e) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.error("Failed to generate board PDF for board {} after {}ms", boardId, durationMs, e);
            throw new IllegalStateException("Failed to generate board PDF", e);
        }
    }

    private static PDFont loadFallbackFont(PDDocument document) throws IOException {
        try (InputStream fallbackStream = BoardPdfRenderer.class.getResourceAsStream(FALLBACK_FONT_RESOURCE)) {
            if (fallbackStream == null) {
                log.warn("Fallback font resource not found at path {}; continuing without fallback font", FALLBACK_FONT_RESOURCE);
                return null;
            }
            return PDType0Font.load(document, fallbackStream);
        }
    }

    record RenderConfig(
            ImageUrlPolicy imageUrlPolicy,
            Duration imageFetchTimeout,
            int maxImageBytesPerImage,
            int maxImageBytesTotal) {

        private static final Duration DEFAULT_IMAGE_FETCH_TIMEOUT = Duration.ofSeconds(2);
        private static final int DEFAULT_MAX_BYTES_PER_IMAGE = 1_048_576;
        private static final int DEFAULT_MAX_BYTES_TOTAL = 10_485_760;

        RenderConfig {
            imageUrlPolicy = imageUrlPolicy == null ? ImageUrlPolicy.fromCsv(ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS) : imageUrlPolicy;
            if (imageFetchTimeout == null || imageFetchTimeout.isNegative() || imageFetchTimeout.isZero()) {
                imageFetchTimeout = DEFAULT_IMAGE_FETCH_TIMEOUT;
            }
            if (maxImageBytesPerImage < 1) {
                maxImageBytesPerImage = DEFAULT_MAX_BYTES_PER_IMAGE;
            }
            if (maxImageBytesTotal < 1) {
                maxImageBytesTotal = DEFAULT_MAX_BYTES_TOTAL;
            }
        }

        static RenderConfig defaults() {
            return new RenderConfig(
                    ImageUrlPolicy.fromCsv(ImageUrlPolicy.DEFAULT_ALLOWED_HOSTS),
                    DEFAULT_IMAGE_FETCH_TIMEOUT,
                    DEFAULT_MAX_BYTES_PER_IMAGE,
                    DEFAULT_MAX_BYTES_TOTAL
            );
        }

        static RenderConfig fromEnvironment() {
            return new RenderConfig(
                    ImageUrlPolicy.fromCsv(System.getenv("BOARDS_IMAGES_ALLOWED_HOSTS")),
                    parseDuration(System.getenv("BOARDS_PDF_IMAGE_FETCH_TIMEOUT"), DEFAULT_IMAGE_FETCH_TIMEOUT),
                    parsePositiveInt(System.getenv("BOARDS_PDF_MAX_IMAGE_BYTES_PER_IMAGE"), DEFAULT_MAX_BYTES_PER_IMAGE),
                    parsePositiveInt(System.getenv("BOARDS_PDF_MAX_IMAGE_BYTES_TOTAL"), DEFAULT_MAX_BYTES_TOTAL)
            );
        }

        private static int parsePositiveInt(String value, int defaultValue) {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                int parsed = Integer.parseInt(value.trim());
                return parsed > 0 ? parsed : defaultValue;
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }

        private static Duration parseDuration(String value, Duration defaultValue) {
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            String trimmed = value.trim().toLowerCase(Locale.ROOT);
            try {
                if (trimmed.endsWith("ms")) {
                    return Duration.ofMillis(Long.parseLong(trimmed.substring(0, trimmed.length() - 2).trim()));
                }
                if (trimmed.endsWith("s")) {
                    return Duration.ofSeconds(Long.parseLong(trimmed.substring(0, trimmed.length() - 1).trim()));
                }
                if (trimmed.endsWith("m")) {
                    return Duration.ofMinutes(Long.parseLong(trimmed.substring(0, trimmed.length() - 1).trim()));
                }
                if (trimmed.startsWith("pt")) {
                    return Duration.parse(trimmed.toUpperCase(Locale.ROOT));
                }
                return Duration.ofMillis(Long.parseLong(trimmed));
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
    }

    static String toFilename(Board board) {
        String base = sanitizeFilePart(board.getTitle());
        String idPart = board.getId() != null ? board.getId().toString() : UUID.randomUUID().toString();
        if (base.isBlank()) {
            base = "board";
        }
        return base + "-" + idPart + ".pdf";
    }

    private static String sanitizeFilePart(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return normalized.replaceAll("^-+|-+$", "");
    }

    private static String cleanMessage(String value) {
        String noHtml = safe(value).replaceAll("(?is)<[^>]*>", " ");
        return noHtml.replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isISOControl(c) || c == '\n' || c == '\t') {
                builder.append(c);
            }
        }
        return builder.toString().trim();
    }

    private static final class PdfCanvas {
        private final PDDocument document;
        private final PDFont primaryFont;
        private final PDFont fallbackFont;
        private PDPage page;
        private PDPageContentStream stream;
        private float cursorY;

        private PdfCanvas(PDDocument document, PDFont primaryFont, PDFont fallbackFont) throws IOException {
            this.document = document;
            this.primaryFont = primaryFont;
            this.fallbackFont = fallbackFont;
            addPage();
        }

        private void drawHeader(Board board) throws IOException {
            float pageWidth = page.getMediaBox().getWidth();
            drawCenteredText("Thank You Board", pageWidth / 2f, cursorY, HEADER_TITLE_SIZE, new Color(17, 24, 39));
            cursorY -= (HEADER_TITLE_SIZE + 8f);

            String boardTitle = safe(board.getTitle());
            String recipient = safe(board.getRecipientName());
            String subtitle = recipient.isBlank() ? boardTitle : boardTitle + ", " + recipient + "!";
            drawCenteredText(subtitle, pageWidth / 2f, cursorY, HEADER_SUBTITLE_SIZE, new Color(31, 41, 55));
            cursorY -= (HEADER_SUBTITLE_SIZE + 8f);

            if (board.getCreatedAt() != null) {
                String dateText = DATE_FORMAT_UTC.format(board.getCreatedAt());
                drawText("Created " + dateText, PAGE_MARGIN, cursorY, META_TEXT_SIZE, new Color(75, 85, 99));
                cursorY -= (META_TEXT_SIZE + 5f);
            }
            drawLine(PAGE_MARGIN, cursorY, pageWidth - PAGE_MARGIN, cursorY, new Color(209, 213, 219), 1f);
            cursorY -= SECTION_GAP;
        }

        private void drawEmptyState() throws IOException {
            ensureSpace(60f);
            drawText("No posts yet.", PAGE_MARGIN, cursorY, BODY_TEXT_SIZE, new Color(75, 85, 99));
            cursorY -= 24f;
        }

        private void drawCards(List<PostCardData> cards) throws IOException {
            float availableWidth = page.getMediaBox().getWidth() - (2f * PAGE_MARGIN);
            int columns = availableWidth >= 460f ? 2 : 1;
            float cardWidth = (availableWidth - (GRID_GAP * (columns - 1))) / columns;

            int index = 0;
            while (index < cards.size()) {
                int rowEnd = Math.min(index + columns, cards.size());
                List<CardLayout> rowLayouts = new ArrayList<>(rowEnd - index);
                float rowHeight = CARD_MIN_HEIGHT;
                for (int i = index; i < rowEnd; i++) {
                    CardLayout layout = CardLayout.of(cards.get(i), cardWidth, primaryFont, fallbackFont);
                    rowLayouts.add(layout);
                    rowHeight = Math.max(rowHeight, layout.cardHeight());
                }

                ensureSpace(rowHeight + GRID_GAP);
                float topY = cursorY;
                for (int col = 0; col < rowLayouts.size(); col++) {
                    float x = PAGE_MARGIN + col * (cardWidth + GRID_GAP);
                    drawCard(x, topY, cardWidth, rowHeight, rowLayouts.get(col));
                }

                cursorY -= (rowHeight + GRID_GAP);
                index = rowEnd;
            }
        }

        private void drawCard(float x, float topY, float width, float height, CardLayout layout) throws IOException {
            float bottomY = topY - height;
            fillRect(x, bottomY, width, height, new Color(255, 255, 255));
            strokeRect(x, bottomY, width, height, new Color(209, 213, 219), 1f);

            float bodyStartY = topY - CARD_PADDING;
            if (layout.imageData() != null) {
                float imageBottom = topY - layout.imageHeight();
                if (layout.imageData().placeholderImage()) {
                    drawImagePlaceholder(x, imageBottom, width, layout.imageHeight());
                } else {
                    stream.drawImage(layout.imageData().image(), x, imageBottom, width, layout.imageHeight());
                }
                bodyStartY = imageBottom - CARD_PADDING;
            }

            float footerBaseline = bottomY + CARD_PADDING;
            float separatorY = footerBaseline + 12f;
            drawLine(x + CARD_PADDING, separatorY, x + width - CARD_PADDING, separatorY, new Color(229, 231, 235), 1f);

            float textBottom = separatorY + 6f;
            float maxTextWidth = width - (2f * CARD_PADDING);
            float maxTextHeight = Math.max(BODY_LINE_HEIGHT, bodyStartY - textBottom);
            int maxLines = Math.max(1, (int) Math.floor(maxTextHeight / BODY_LINE_HEIGHT));
            List<String> clippedMessage = clipLines(layout.messageLines(), maxLines, primaryFont, fallbackFont, BODY_TEXT_SIZE, maxTextWidth);

            float textY = bodyStartY - BODY_TEXT_SIZE;
            for (String line : clippedMessage) {
                drawText(line, x + CARD_PADDING, textY, BODY_TEXT_SIZE, new Color(31, 41, 55));
                textY -= BODY_LINE_HEIGHT;
            }

            String author = fitSingleLine("From " + layout.authorName(), primaryFont, fallbackFont, FOOTER_TEXT_SIZE, maxTextWidth);
            float authorWidth = textWidth(author, primaryFont, fallbackFont, FOOTER_TEXT_SIZE);
            drawText(author, x + width - CARD_PADDING - authorWidth, footerBaseline, FOOTER_TEXT_SIZE, new Color(75, 85, 99));
        }

        private void drawImagePlaceholder(float x, float y, float width, float height) throws IOException {
            fillRect(x, y, width, height, new Color(243, 244, 246));
            drawLine(x + 10f, y + 10f, x + width - 10f, y + height - 10f, new Color(209, 213, 219), 1f);
            drawLine(x + width - 10f, y + 10f, x + 10f, y + height - 10f, new Color(209, 213, 219), 1f);
            String label = "No image available";
            float labelWidth = textWidth(label, primaryFont, fallbackFont, FOOTER_TEXT_SIZE);
            float labelX = x + (width - labelWidth) / 2f;
            float labelY = y + (height / 2f) - (FOOTER_TEXT_SIZE / 2f);
            drawText(label, labelX, labelY, FOOTER_TEXT_SIZE, new Color(107, 114, 128));
        }

        private void drawCenteredText(String text, float centerX, float y, float size, Color color) throws IOException {
            List<FontRun> runs = getFontRuns(text);
            float width = textWidth(runs, size);
            drawTextRuns(runs, centerX - (width / 2f), y, size, color);
        }

        private void drawText(String text, float x, float y, float size, Color color) throws IOException {
            List<FontRun> runs = getFontRuns(text);
            drawTextRuns(runs, x, y, size, color);
        }

        private void drawTextRuns(List<FontRun> runs, float x, float y, float size, Color color) throws IOException {
            if (runs.isEmpty()) {
                return;
            }
            ensureSpace(size + 4f);
            stream.beginText();
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, y);
            for (FontRun run : runs) {
                stream.setFont(run.font(), size);
                stream.showText(run.text());
            }
            stream.endText();
        }

        private void fillRect(float x, float y, float width, float height, Color color) throws IOException {
            stream.setNonStrokingColor(color);
            stream.addRect(x, y, width, height);
            stream.fill();
        }

        private void strokeRect(float x, float y, float width, float height, Color color, float lineWidth) throws IOException {
            stream.setStrokingColor(color);
            stream.setLineWidth(lineWidth);
            stream.addRect(x, y, width, height);
            stream.stroke();
        }

        private void drawLine(float x1, float y1, float x2, float y2, Color color, float lineWidth) throws IOException {
            stream.setStrokingColor(color);
            stream.setLineWidth(lineWidth);
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
            stream.stroke();
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (cursorY - requiredHeight < PAGE_MARGIN) {
                addPage();
            }
        }

        private void addPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            fillRect(0f, 0f, page.getMediaBox().getWidth(), page.getMediaBox().getHeight(), new Color(249, 250, 251));
            cursorY = page.getMediaBox().getHeight() - PAGE_MARGIN;
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }

        private List<FontRun> getFontRuns(String text) {
            return buildFontRuns(safe(text), primaryFont, fallbackFont);
        }

        private static List<FontRun> buildFontRuns(String normalized, PDFont primary, PDFont fallback) {
            if (normalized == null || normalized.isEmpty()) {
                return List.of();
            }
            List<FontRun> runs = new ArrayList<>();
            StringBuilder primaryBuf = new StringBuilder();
            StringBuilder fallbackBuf = new StringBuilder();
            PDFont lastFont = null;
            for (int i = 0; i < normalized.length(); ) {
                int codePoint = Character.codePointAt(normalized, i);
                String ch = new String(Character.toChars(codePoint));
                i += Character.charCount(codePoint);
                PDFont which = null;
                if (canEncode(primary, ch)) {
                    which = primary;
                } else if (fallback != null && canEncode(fallback, ch)) {
                    which = fallback;
                } else {
                    which = primary;
                    ch = "?";
                }
                if (which == primary) {
                    if (lastFont == fallback && fallbackBuf.length() > 0) {
                        runs.add(new FontRun(fallback, fallbackBuf.toString()));
                        fallbackBuf.setLength(0);
                    }
                    primaryBuf.append(ch);
                    lastFont = primary;
                } else {
                    if (lastFont == primary && primaryBuf.length() > 0) {
                        runs.add(new FontRun(primary, primaryBuf.toString()));
                        primaryBuf.setLength(0);
                    }
                    fallbackBuf.append(ch);
                    lastFont = fallback;
                }
            }
            if (primaryBuf.length() > 0) {
                runs.add(new FontRun(primary, primaryBuf.toString()));
            }
            if (fallbackBuf.length() > 0) {
                runs.add(new FontRun(fallback, fallbackBuf.toString()));
            }
            return runs;
        }

        private static boolean canEncode(PDFont font, String oneChar) {
            try {
                font.encode(oneChar);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }

        private float textWidth(List<FontRun> runs, float size) throws IOException {
            float total = 0f;
            for (FontRun run : runs) {
                total += run.font().getStringWidth(run.text()) / 1000f * size;
            }
            return total;
        }

        private static float textWidth(String text, PDFont primary, PDFont fallback, float size) throws IOException {
            List<FontRun> runs = buildFontRuns(safe(text), primary, fallback);
            float total = 0f;
            for (FontRun run : runs) {
                total += run.font().getStringWidth(run.text()) / 1000f * size;
            }
            return total;
        }

        /** Used by outer class for clipLines/fitSingleLine. */
        private static float textWidthForString(String text, PDFont primary, PDFont fallback, float size) throws IOException {
            return textWidth(text, primary, fallback, size);
        }

        private static List<String> wrap(String text, PDFont primary, PDFont fallback, float fontSize, float maxWidth) throws IOException {
            String normalized = safe(text);
            if (normalized.isBlank()) {
                return List.of("");
            }
            List<String> lines = new ArrayList<>();
            for (String paragraph : normalized.split("\\R")) {
                String trimmed = paragraph.trim();
                if (trimmed.isBlank()) {
                    lines.add("");
                    continue;
                }
                StringBuilder currentLine = new StringBuilder();
                for (String word : trimmed.split("\\s+")) {
                    String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
                    float candidateWidth = textWidth(candidate, primary, fallback, fontSize);
                    if (candidateWidth > maxWidth && !currentLine.isEmpty()) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        currentLine = new StringBuilder(candidate);
                    }
                }
                lines.add(currentLine.toString());
            }
            return lines;
        }
    }

    private record FontRun(PDFont font, String text) {
    }

    private static final class PostCardData {
        private final String authorName;
        private final String messageText;
        private final ImageData imageData;

        private PostCardData(String authorName, String messageText, ImageData imageData) {
            this.authorName = authorName;
            this.messageText = messageText;
            this.imageData = imageData;
        }

        private static PostCardData from(Post post, PDDocument document, PDFont primaryFont, PDFont fallbackFont, RenderConfig renderConfig, long[] remainingImageBytes) {
            String author = safe(post.getAuthorName());
            if (author.isBlank()) {
                author = "Anonymous";
            }
            String message = cleanMessage(post.getMessageText());
            if (message.isBlank()) {
                message = "(No message)";
            }
            String imageUrl = (post.getUploadedImageUrl() != null && !post.getUploadedImageUrl().isBlank())
                    ? post.getUploadedImageUrl()
                    : post.getGiphyUrl();
            return new PostCardData(author, message, tryLoadImage(imageUrl, document, renderConfig, remainingImageBytes));
        }
    }

    private record CardLayout(String authorName, List<String> messageLines, ImageData imageData, float imageHeight, float cardHeight) {
        private static CardLayout of(PostCardData data, float cardWidth, PDFont primaryFont, PDFont fallbackFont) throws IOException {
            float maxTextWidth = cardWidth - (2f * CARD_PADDING);
            List<String> messageLines = PdfCanvas.wrap(data.messageText, primaryFont, fallbackFont, BODY_TEXT_SIZE, maxTextWidth);
            float textHeight = Math.max(BODY_LINE_HEIGHT, messageLines.size() * BODY_LINE_HEIGHT);

            float imageHeight = 0f;
            if (data.imageData != null) {
                if (data.imageData.placeholderImage()) {
                    imageHeight = PLACEHOLDER_IMAGE_HEIGHT;
                } else if (data.imageData.width() > 0) {
                    float ratio = (float) data.imageData.height() / (float) data.imageData.width();
                    imageHeight = clamp(cardWidth * ratio, IMAGE_MIN_HEIGHT, IMAGE_MAX_HEIGHT);
                }
            }

            float bodyHeight = CARD_PADDING + textHeight + 20f + 24f + CARD_PADDING;
            float height = Math.max(CARD_MIN_HEIGHT, imageHeight + bodyHeight);
            return new CardLayout(data.authorName, messageLines, data.imageData, imageHeight, height);
        }
    }

    private record ImageData(PDImageXObject image, int width, int height, boolean placeholderImage) {
        private static ImageData placeholderData() {
            return new ImageData(null, 0, 0, true);
        }
    }

    private static ImageData tryLoadImage(String imageUrl, PDDocument document, RenderConfig renderConfig, long[] remainingImageBytes) {
        String normalized = safe(imageUrl);
        if (normalized.isBlank()) {
            return null;
        }

        boolean isDataUri = normalized.startsWith("data:image/");
        if (!isDataUri && !renderConfig.imageUrlPolicy().isAllowedHttpUrl(normalized)) {
            log.debug("Image URL '{}' is not allowed by image URL policy; rendering placeholder image instead", normalized);
            return ImageData.placeholderData();
        }

        byte[] imageBytes = readImageBytes(normalized, renderConfig, remainingImageBytes);
        if (imageBytes == null || imageBytes.length == 0) {
            log.debug("Failed to load image bytes from URL '{}'; rendering placeholder image instead", normalized);
            return ImageData.placeholderData();
        }
        try {
            PDImageXObject image = PDImageXObject.createFromByteArray(document, imageBytes, "post-image");
            return new ImageData(image, image.getWidth(), image.getHeight(), false);
        } catch (IOException ignored) {
            return ImageData.placeholderData();
        }
    }

    private static byte[] readImageBytes(String imageUrl, RenderConfig renderConfig, long[] remainingImageBytes) {
        if (remainingImageBytes[0] <= 0) {
            log.debug("Global image byte budget exhausted; skipping image {}", imageUrl);
            return null;
        }
        int maxReadableBytes = (int) Math.min((long) renderConfig.maxImageBytesPerImage(), remainingImageBytes[0]);
        if (maxReadableBytes < 1) {
            return null;
        }

        if (imageUrl.startsWith("data:image/")) {
            byte[] decoded = decodeDataUri(imageUrl);
            if (decoded == null || decoded.length == 0 || decoded.length > maxReadableBytes) {
                log.debug("Data URI image was empty, invalid, or exceeded per-image limit (limit={} bytes)", maxReadableBytes);
                return null;
            }
            remainingImageBytes[0] -= decoded.length;
            return decoded;
        }

        if (!(imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
            log.debug("Unsupported image URL scheme for '{}'; only http/https and data:image URIs are supported", imageUrl);
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                    .timeout(renderConfig.imageFetchTimeout())
                    .header("Accept", "image/*")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.debug("Image request to '{}' returned non-success status code {}", imageUrl, response.statusCode());
                return null;
            }
            byte[] body = readWithCap(response.body(), maxReadableBytes);
            if (body == null || body.length == 0) {
                return null;
            }
            remainingImageBytes[0] -= body.length;
            return body;
        } catch (Exception e) {
            log.debug("Exception while fetching image from '{}'", imageUrl, e);
            return null;
        }
    }

    private static byte[] readWithCap(InputStream inputStream, int maxBytes) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 4096))) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return null;
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static byte[] decodeDataUri(String value) {
        int commaIndex = value.indexOf(',');
        if (commaIndex < 0 || commaIndex == value.length() - 1) {
            return null;
        }
        String metadata = value.substring(0, commaIndex).toLowerCase(Locale.ROOT);
        String payload = value.substring(commaIndex + 1);
        try {
            if (metadata.contains(";base64")) {
                return Base64.getDecoder().decode(payload);
            }
            return URLDecoder.decode(payload, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<String> clipLines(List<String> lines, int maxLines, PDFont primary, PDFont fallback, float fontSize, float width) throws IOException {
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<String> clipped = new ArrayList<>(maxLines);
        for (int i = 0; i < maxLines; i++) {
            clipped.add(lines.get(i));
        }
        String lastLine = clipped.get(maxLines - 1);
        clipped.set(maxLines - 1, fitSingleLine(lastLine + "…", primary, fallback, fontSize, width));
        return clipped;
    }

    private static String fitSingleLine(String text, PDFont primary, PDFont fallback, float size, float width) throws IOException {
        String normalized = safe(text);
        if (PdfCanvas.textWidthForString(normalized, primary, fallback, size) <= width) {
            return normalized;
        }
        String suffix = "…";
        StringBuilder sb = new StringBuilder(normalized);
        while (sb.length() > 0 && PdfCanvas.textWidthForString(sb.toString() + suffix, primary, fallback, size) > width) {
            int len = sb.length();
            if (len >= 2 && Character.isSurrogatePair(sb.charAt(len - 2), sb.charAt(len - 1))) {
                sb.delete(len - 2, len);
            } else {
                sb.deleteCharAt(len - 1);
            }
        }
        return sb.length() == 0 ? suffix : sb + suffix;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
