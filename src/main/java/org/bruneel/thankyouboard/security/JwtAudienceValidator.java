package org.bruneel.thankyouboard.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Validates that the JWT contains the configured resource-server audience.
 */
public final class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String audience;

    public JwtAudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiences = token.getAudience();
        if (audiences != null && audiences.contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error err = new OAuth2Error(
                "invalid_token",
                "The required audience is missing",
                null
        );
        return OAuth2TokenValidatorResult.failure(err);
    }
}
