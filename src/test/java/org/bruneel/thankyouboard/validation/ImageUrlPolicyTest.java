package org.bruneel.thankyouboard.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ImageUrlPolicyTest {

    @Test
    void fromCsv_nullUsesDefaultHosts() {
        ImageUrlPolicy policy = ImageUrlPolicy.fromCsv(null);
        assertThat(policy.normalizeAndValidateHttpsUrl("https://media.giphy.com/x.gif")).isNotNull();
    }

    @Test
    void fromCsv_blankUsesDefaultHosts() {
        ImageUrlPolicy policy = ImageUrlPolicy.fromCsv("   ");
        assertThat(policy.normalizeAndValidateHttpsUrl("https://giphy.com/x")).isNotNull();
    }

    @Test
    void fromCsv_customHost() {
        ImageUrlPolicy policy = ImageUrlPolicy.fromCsv("example.com,*.cdn.example.com");
        assertThat(policy.normalizeAndValidateHttpsUrl("https://example.com/p")).isNotNull();
        assertThat(policy.normalizeAndValidateHttpsUrl("https://sub.cdn.example.com/p")).isNotNull();
        assertThat(policy.normalizeAndValidateHttpsUrl("https://evil.com/p")).isNull();
    }

    @Test
    void fromCsv_emptyTokensAfterSplitFallsBackToDefaults() {
        ImageUrlPolicy policy = ImageUrlPolicy.fromCsv(",,,");
        assertThat(policy.normalizeAndValidateHttpsUrl("https://giphy.com/x")).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
            "https://giphy.com/foo.gif, https://giphy.com/foo.gif",
            "https://media.giphy.com/foo.gif, https://media.giphy.com/foo.gif",
    })
    void normalizeAndValidateHttpsUrl_acceptsAllowedHosts(String input, String expected) {
        ImageUrlPolicy policy = ImageUrlPolicy.fromCsv("giphy.com,*.giphy.com");
        assertThat(policy.normalizeAndValidateHttpsUrl(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://giphy.com/x",
            "ftp://giphy.com/x",
            "not a url",
            "https://evil.com/x",
    })
    void normalizeAndValidateHttpsUrl_rejectsBadInput(String input) {
        ImageUrlPolicy policy = ImageUrlPolicy.fromCsv("giphy.com");
        assertThat(policy.normalizeAndValidateHttpsUrl(input)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void normalizeAndValidateHttpsUrl_rejectsBlank(String input) {
        ImageUrlPolicy policy = ImageUrlPolicy.fromCsv("giphy.com");
        assertThat(policy.normalizeAndValidateHttpsUrl(input)).isNull();
    }

    @Test
    void normalizeAndValidateHttpOrHttpsUrl_acceptsHttpForAllowedHost() {
        ImageUrlPolicy policy = ImageUrlPolicy.fromCsv("localhost");
        assertThat(policy.normalizeAndValidateHttpOrHttpsUrl("http://localhost:8080/x")).isNotNull();
    }

    @Test
    void isAllowedHttpUrl_matchesWildcardSubdomain() {
        ImageUrlPolicy policy = ImageUrlPolicy.fromCsv("*.giphy.com");
        assertThat(policy.isAllowedHttpUrl("http://media.giphy.com/x")).isTrue();
        assertThat(policy.isAllowedHttpUrl("http://giphy.com/x")).isTrue();
    }

    @Test
    void normalizeHost_trimsTrailingDotOnHost() {
        ImageUrlPolicy policy = ImageUrlPolicy.fromCsv("giphy.com.");
        assertThat(policy.normalizeAndValidateHttpsUrl("https://giphy.com./x")).isNotNull();
    }
}
