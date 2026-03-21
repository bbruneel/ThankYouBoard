package org.bruneel.thankyouboard.domain;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PostTest {

    @Test
    void gettersAndSetters_roundTrip() {
        Post post = new Post();
        UUID id = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        ZonedDateTime now = ZonedDateTime.now();

        post.setId(id);
        post.setBoardId(boardId);
        post.setAuthorName("Author");
        post.setMessageText("Message");
        post.setGiphyUrl("https://giphy.com/x");
        post.setCreatedAt(now);

        assertThat(post.getId()).isEqualTo(id);
        assertThat(post.getBoardId()).isEqualTo(boardId);
        assertThat(post.getAuthorName()).isEqualTo("Author");
        assertThat(post.getMessageText()).isEqualTo("Message");
        assertThat(post.getGiphyUrl()).isEqualTo("https://giphy.com/x");
        assertThat(post.getCreatedAt()).isEqualTo(now);
    }
}
