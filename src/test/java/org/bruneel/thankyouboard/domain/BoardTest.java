package org.bruneel.thankyouboard.domain;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BoardTest {

    @Test
    void gettersAndSetters_roundTrip() {
        Board board = new Board();
        UUID id = UUID.randomUUID();
        ZonedDateTime now = ZonedDateTime.now();

        board.setId(id);
        board.setTitle("Test Board");
        board.setRecipientName("Recipient");
        board.setCreatedAt(now);

        assertThat(board.getId()).isEqualTo(id);
        assertThat(board.getTitle()).isEqualTo("Test Board");
        assertThat(board.getRecipientName()).isEqualTo("Recipient");
        assertThat(board.getCreatedAt()).isEqualTo(now);
    }
}
