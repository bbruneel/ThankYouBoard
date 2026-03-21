package org.bruneel.thankyouboard.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ImageUrlPolicy {

    static final String DEFAULT_ALLOWED_HOSTS =
            "giphy.com,*.giphy.com,giphyusercontent.com,*.giphyusercontent.com";

    private final List<String> allowedHostPatterns;

    private ImageUrlPolicy(List<String> allowedHostPatterns) {
        this.allowedHostPatterns = allowedHostPatterns;
    }

    static ImageUrlPolicy fromCsv(String csv) {
        String source = (csv == null || csv.isBlank()) ? DEFAULT_ALLOWED_HOSTS : csv;
        List<String> patterns = new ArrayList<>();
        for (String token : source.split(",")) {
            String normalized = normalizeHost(token);
            if (!normalized.isBlank()) {
                patterns.add(normalized);
            }
        }
        if (patterns.isEmpty()) {
            for (String token : DEFAULT_ALLOWED_HOSTS.split(",")) {
                String normalized = normalizeHost(token);
                if (!normalized.isBlank()) {
                    patterns.add(normalized);
                }
            }
        }
        return new ImageUrlPolicy(List.copyOf(patterns));
    }

    String normalizeAndValidateHttpsUrl(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return null;
        }
        if (!"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        String normalizedHost = normalizeHost(host);
        if (!isAllowedHost(normalizedHost)) {
            return null;
        }
        return uri.toString();
    }

    String normalizeAndValidateHttpOrHttpsUrl(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return null;
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        String normalizedHost = normalizeHost(host);
        if (!isAllowedHost(normalizedHost)) {
            return null;
        }
        return uri.toString();
    }

    boolean isAllowedHttpUrl(String rawUrl) {
        String trimmed = rawUrl == null ? "" : rawUrl.trim();
        if (trimmed.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            return false;
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        return isAllowedHost(normalizeHost(host));
    }

    private boolean isAllowedHost(String host) {
        for (String pattern : allowedHostPatterns) {
            if (pattern.startsWith("*.")) {
                String suffix = pattern.substring(2);
                if (host.equals(suffix) || host.endsWith("." + suffix)) {
                    return true;
                }
                continue;
            }
            if (host.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeHost(String rawHost) {
        String lower = rawHost == null ? "" : rawHost.trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".")) {
            return lower.substring(0, lower.length() - 1);
        }
        return lower;
    }
}
