package org.bruneel.thankyouboard.service;

import org.bruneel.thankyouboard.model.Post;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HexFormat;

public class CapabilityTokenService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final int randomBytes;
    private final Duration expiryDuration;

    public CapabilityTokenService(int randomBytes, long expiryHours) {
        if (randomBytes < 16) {
            throw new IllegalArgumentException("randomBytes must be >= 16");
        }
        if (expiryHours < 1) {
            throw new IllegalArgumentException("expiryHours must be >= 1");
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

    public record CapabilityTokenValidation(boolean valid, CapabilityTokenFailureReason reason) {}

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

    public CapabilityTokenValidation validateForPost(String rawToken, Post post) {
        if (rawToken == null || rawToken.isBlank()) {
            return new CapabilityTokenValidation(false, CapabilityTokenFailureReason.MISSING_TOKEN);
        }
        if (post == null || post.getCapabilityTokenHash() == null || post.getCapabilityTokenExpiresAt() == null) {
            return new CapabilityTokenValidation(false, CapabilityTokenFailureReason.POST_HAS_NO_TOKEN);
        }
        if (post.getCapabilityTokenExpiresAt().isBefore(ZonedDateTime.now(ZoneOffset.UTC))) {
            return new CapabilityTokenValidation(false, CapabilityTokenFailureReason.EXPIRED);
        }

        byte[] tokenHashBytes = sha256Bytes(rawToken);
        byte[] storedHashBytes;
        try {
            storedHashBytes = HexFormat.of().parseHex(post.getCapabilityTokenHash());
        } catch (IllegalArgumentException e) {
            return new CapabilityTokenValidation(false, CapabilityTokenFailureReason.INVALID_STORED_HASH);
        }

        // Constant-time comparison to avoid timing leaks.
        boolean match = MessageDigest.isEqual(tokenHashBytes, storedHashBytes);
        return match
                ? new CapabilityTokenValidation(true, null)
                : new CapabilityTokenValidation(false, CapabilityTokenFailureReason.INVALID_TOKEN);
    }

    private static byte[] sha256Bytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }
}

