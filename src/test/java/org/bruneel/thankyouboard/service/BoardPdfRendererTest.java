package org.bruneel.thankyouboard.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.bruneel.thankyouboard.domain.Board;
import org.bruneel.thankyouboard.domain.Post;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BoardPdfRendererTest {

    @Test
    void render_producesNonEmptyPdfWithOnePage() throws Exception {
        Board board = new Board();
        board.setId(UUID.randomUUID());
        board.setOwnerId("auth0|x");
        board.setTitle("Team Thanks");
        board.setRecipientName("Alex");
        board.setCreatedAt(ZonedDateTime.now());

        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setBoardId(board.getId());
        post.setAuthorName("Sam");
        post.setMessageText("Great work on the release.");
        post.setCreatedAt(ZonedDateTime.now());

        byte[] pdf = BoardPdfRenderer.render(board, List.of(post));

        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void render_withDisplayTimeZone_stillProducesPdf() throws Exception {
        Board board = new Board();
        board.setId(UUID.randomUUID());
        board.setOwnerId("auth0|x");
        board.setTitle("Birthday");
        board.setRecipientName("Jordan");
        board.setCreatedAt(ZonedDateTime.now());

        byte[] pdf = BoardPdfRenderer.render(board, List.of(), BoardPdfRenderer.RenderConfig.defaults(), ZoneId.of("Europe/Brussels"));

        assertThat(pdf).isNotEmpty();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void toFilename_sanitizesTitleAndIncludesId() {
        Board board = new Board();
        UUID id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        board.setId(id);
        board.setTitle("Hello World!!");

        String name = BoardPdfRenderer.toFilename(board);

        assertThat(name).endsWith("-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.pdf");
        assertThat(name).contains("hello");
    }
}
