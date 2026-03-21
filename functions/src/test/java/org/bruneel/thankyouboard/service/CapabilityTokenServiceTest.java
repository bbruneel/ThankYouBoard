package org.bruneel.thankyouboard.service;

import org.bruneel.thankyouboard.model.Post;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityTokenServiceTest {

    @Test
    void issueToken_producesTokenAndSha256Hash() {
        CapabilityTokenService service = new CapabilityTokenService(32, 24);

        CapabilityTokenService.IssuedToken issued = service.issueToken();

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.tokenHashHex()).matches("^[0-9a-f]{64}$");
        assertThat(issued.expiresAt()).isAfter(ZonedDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void validateForPost_acceptsValidToken() {
        CapabilityTokenService service = new CapabilityTokenService(32, 24);
        CapabilityTokenService.IssuedToken issued = service.issueToken();

        Post post = new Post();
        post.setCapabilityTokenHash(issued.tokenHashHex());
        post.setCapabilityTokenExpiresAt(issued.expiresAt());

        assertThat(service.validateForPost(issued.token(), post).valid()).isTrue();
    }

    @Test
    void validateForPost_rejectsWrongToken() {
        CapabilityTokenService service = new CapabilityTokenService(32, 24);
        CapabilityTokenService.IssuedToken issued = service.issueToken();

        Post post = new Post();
        post.setCapabilityTokenHash(issued.tokenHashHex());
        post.setCapabilityTokenExpiresAt(issued.expiresAt());

        assertThat(service.validateForPost("wrong", post).valid()).isFalse();
    }

    @Test
    void validateForPost_rejectsExpiredToken() {
        CapabilityTokenService service = new CapabilityTokenService(32, 24);
        CapabilityTokenService.IssuedToken issued = service.issueToken();

        Post post = new Post();
        post.setCapabilityTokenHash(issued.tokenHashHex());
        post.setCapabilityTokenExpiresAt(ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(5));

        assertThat(service.validateForPost(issued.token(), post).valid()).isFalse();
    }
}

