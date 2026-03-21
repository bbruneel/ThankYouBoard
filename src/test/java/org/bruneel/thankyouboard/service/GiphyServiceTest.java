package org.bruneel.thankyouboard.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GiphyServiceTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    private static RestClient.Builder createBuilderMock(RestClient restClient) {
        RestClient.Builder builder = org.mockito.Mockito.mock(RestClient.Builder.class);
        when(builder.build()).thenReturn(restClient);
        return builder;
    }

    @Test
    void isConfigured_returnsFalseWhenApiKeyEmpty() {
        GiphyService service = new GiphyService(createBuilderMock(restClient), "");
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_returnsFalseWhenApiKeyNull() {
        GiphyService service = new GiphyService(createBuilderMock(restClient), null);
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_returnsFalseWhenApiKeyWhitespaceOnly() {
        GiphyService service = new GiphyService(createBuilderMock(restClient), "   ");
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_returnsTrueWhenApiKeySet() {
        GiphyService service = new GiphyService(createBuilderMock(restClient), "test-key");
        assertThat(service.isConfigured()).isTrue();
    }

    @Test
    void isConfigured_returnsTrueWhenApiKeyHasLeadingTrailingSpaces() {
        GiphyService service = new GiphyService(createBuilderMock(restClient), "  key  ");
        assertThat(service.isConfigured()).isTrue();
    }
}
