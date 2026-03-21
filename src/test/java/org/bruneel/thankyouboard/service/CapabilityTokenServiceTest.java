package org.bruneel.thankyouboard.service;

import org.bruneel.thankyouboard.domain.Post;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityTokenServiceTest {

    @Test
    void issueToken_producesUsableTokenAndHash() {
        CapabilityTokenService service = new CapabilityTokenService(32, 24);

        CapabilityTokenService.IssuedToken issued = service.issueToken();

        assertNotNull(issued.token());
        assertFalse(issued.token().isBlank());
        assertNotNull(issued.tokenHashHex());
        assertTrue(issued.tokenHashHex().matches("^[0-9a-f]{64}$"), "Expected SHA-256 hex hash");
        assertNotNull(issued.expiresAt());
        assertTrue(issued.expiresAt().isAfter(ZonedDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    void isValidForPost_returnsTrueForCorrectTokenAndNonExpiredPost() {
        CapabilityTokenService service = new CapabilityTokenService(32, 24);

        CapabilityTokenService.IssuedToken issued = service.issueToken();

        Post post = new Post();
        post.setCapabilityTokenHash(issued.tokenHashHex());
        post.setCapabilityTokenExpiresAt(issued.expiresAt());

        assertTrue(service.isValidForPost(issued.token(), post));
    }

    @Test
    void isValidForPost_returnsFalseForWrongToken() {
        CapabilityTokenService service = new CapabilityTokenService(32, 24);

        CapabilityTokenService.IssuedToken issued = service.issueToken();

        Post post = new Post();
        post.setCapabilityTokenHash(issued.tokenHashHex());
        post.setCapabilityTokenExpiresAt(issued.expiresAt());

        assertFalse(service.isValidForPost("definitely-wrong", post));
    }

    @Test
    void isValidForPost_returnsFalseForExpiredPost() {
        CapabilityTokenService service = new CapabilityTokenService(32, 24);

        CapabilityTokenService.IssuedToken issued = service.issueToken();

        Post post = new Post();
        post.setCapabilityTokenHash(issued.tokenHashHex());
        post.setCapabilityTokenExpiresAt(ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(5));

        assertFalse(service.isValidForPost(issued.token(), post));
    }
}

