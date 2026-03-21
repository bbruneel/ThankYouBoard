package org.bruneel.thankyouboard.validation;

import java.time.ZoneId;
import java.util.regex.Pattern;

/**
 * Validates and parses timezone values from clients (header or body) to prevent injection.
 * Only allows IANA zone IDs and zone-offset patterns; rejects anything that could be
 * used in scripting, HTML, or other injection contexts.
 */
public final class TimeZoneValidator {

    /** Max length matching pdf_job.time_zone column. */
    private static final int MAX_LENGTH = 64;

    /**
     * IANA region IDs use letters, digits, '_', '/'; offsets use '+', '-', ':' and digits.
     * Explicitly disallow newlines, angle brackets, quotes, semicolons, backslash, etc.
     */
    private static final Pattern SAFE_ZONE_PATTERN = Pattern.compile("^[A-Za-z0-9/_+\\-:]+$");

    private TimeZoneValidator() {
    }

    /**
     * Returns true if the value is safe to store or use: length and character set only.
     */
    public static boolean isValidFormat(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.length() <= MAX_LENGTH && SAFE_ZONE_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Parses the value into a ZoneId if it passes format validation and is a known zone.
     * Returns null for null/blank, invalid format, or unknown zone (no exception).
     */
    public static ZoneId parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH || !SAFE_ZONE_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        try {
            return ZoneId.of(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Returns the trimmed value if it passes validation and parses as a valid ZoneId; otherwise null.
     * Use this when you need to store the value (e.g. in DB) so only validated strings are persisted.
     */
    public static String sanitizeForStore(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH || !SAFE_ZONE_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        try {
            ZoneId.of(trimmed);
            return trimmed;
        } catch (Exception ignored) {
            return null;
        }
    }
}
