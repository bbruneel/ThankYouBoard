package org.bruneel.thankyouboard.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAudienceValidatorTest {

    private static Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
    }

    @Test
    void validate_succeedsWhenAudienceMatches() {
        JwtAudienceValidator validator = new JwtAudienceValidator("urn:api");
        Jwt jwt = baseJwt().audience(List.of("urn:api")).build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void validate_succeedsWhenMultipleAudiencesAndOneMatches() {
        JwtAudienceValidator validator = new JwtAudienceValidator("urn:api");
        Jwt jwt = baseJwt().audience(List.of("urn:other", "urn:api")).build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void validate_failsWhenAudienceMissing() {
        JwtAudienceValidator validator = new JwtAudienceValidator("urn:api");
        Jwt jwt = baseJwt().audience(List.of("urn:other")).build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void validate_failsWhenAudiencesNull() {
        JwtAudienceValidator validator = new JwtAudienceValidator("urn:api");
        Jwt jwt = baseJwt().build();

        assertThat(jwt.getAudience()).isNull();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void validate_failsWhenAudiencesEmpty() {
        JwtAudienceValidator validator = new JwtAudienceValidator("urn:api");
        Jwt jwt = baseJwt().audience(List.of()).build();

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }
}
