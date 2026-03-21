package org.bruneel.thankyouboard.service;

import org.bruneel.thankyouboard.domain.Post;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class CapabilityTokenService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final int randomBytes;
    private final Duration expiryDuration;

    public CapabilityTokenService(
            @Value("${posts.capability-token.random-bytes:32}") int randomBytes,
            @Value("${posts.capability-token.expiry-hours:24}") long expiryHours
    ) {
        if (randomBytes < 16) {
            throw new IllegalArgumentException("posts.capability-token.random-bytes must be >= 16");
        }
        if (expiryHours < 1) {
            throw new IllegalArgumentException("posts.capability-token.expiry-hours must be >= 1");
        }
        this.randomBytes = randomBytes;
        this.expiryDuration = Duration.ofHours(expiryHours);
    }

    public record IssuedToken(String token, String tokenHashHex, ZonedDateTime expiresAt) {}

    public enum CapabilityTokenFailureReason {
        MISSING_TOKEN,
        POST_HAS_NO_TOKEN,
        EXPIRED,
        INVALID_TOKEN,
        INVALID_STORED_HASH
    }

    public record CapabilityTokenValidation(boolean valid, CapabilityTokenFailureReason reason) {
        public static CapabilityTokenValidation ok() {
            return new CapabilityTokenValidation(true, null);
        }
        public static CapabilityTokenValidation invalid(CapabilityTokenFailureReason reason) {
            return new CapabilityTokenValidation(false, reason);
        }
    }

    public IssuedToken issueToken() {
        byte[] secret = new byte[randomBytes];
        secureRandom.nextBytes(secret);
        // URL-safe token suitable for headers and JSON without escaping.
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        byte[] hashBytes = sha256Bytes(token);
        String hashHex = HexFormat.of().formatHex(hashBytes);
        ZonedDateTime expiresAt = ZonedDateTime.now(ZoneOffset.UTC).plus(expiryDuration);
        return new IssuedToken(token, hashHex, expiresAt);
    }

    public boolean isValidForPost(String rawToken, Post post) {
        return validateForPost(rawToken, post).valid;
    }

    public CapabilityTokenValidation validateForPost(String rawToken, Post post) {
        if (rawToken == null || rawToken.isBlank()) {
            return CapabilityTokenValidation.invalid(CapabilityTokenFailureReason.MISSING_TOKEN);
        }
        if (post == null || post.getCapabilityTokenHash() == null || post.getCapabilityTokenExpiresAt() == null) {
            return CapabilityTokenValidation.invalid(CapabilityTokenFailureReason.POST_HAS_NO_TOKEN);
        }
        if (post.getCapabilityTokenExpiresAt().isBefore(ZonedDateTime.now(ZoneOffset.UTC))) {
            return CapabilityTokenValidation.invalid(CapabilityTokenFailureReason.EXPIRED);
        }

        byte[] tokenHashBytes = sha256Bytes(rawToken);
        byte[] storedHashBytes;
        try {
            storedHashBytes = HexFormat.of().parseHex(post.getCapabilityTokenHash());
        } catch (IllegalArgumentException e) {
            return CapabilityTokenValidation.invalid(CapabilityTokenFailureReason.INVALID_STORED_HASH);
        }

        // Constant-time comparison to avoid timing leaks.
        boolean match = MessageDigest.isEqual(tokenHashBytes, storedHashBytes);
        return match
                ? CapabilityTokenValidation.ok()
                : CapabilityTokenValidation.invalid(CapabilityTokenFailureReason.INVALID_TOKEN);
    }

    private static byte[] sha256Bytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // SHA-256 should always be available in the JRE.
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }
}

