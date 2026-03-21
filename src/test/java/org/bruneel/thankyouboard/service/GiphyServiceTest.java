package org.bruneel.thankyouboard.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_returnsBodyWhenConfigured() {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(Function.class))).thenReturn(uriSpec);
        when(uriSpec.accept(any(MediaType.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"data\":[],\"pagination\":{}}");

        GiphyService service = new GiphyService(createBuilderMock(restClient), "k");
        assertThat(service.search("cats", 5, 0)).isEqualTo("{\"data\":[],\"pagination\":{}}");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_usesEmptyQueryWhenQIsNull() {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(Function.class))).thenReturn(uriSpec);
        when(uriSpec.accept(any(MediaType.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("ok");

        GiphyService service = new GiphyService(createBuilderMock(restClient), "k");
        assertThat(service.search(null, 1, 0)).isEqualTo("ok");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void trending_returnsBodyWhenConfigured() {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(Function.class))).thenReturn(uriSpec);
        when(uriSpec.accept(any(MediaType.class))).thenReturn(uriSpec);
        when(uriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"data\":[]}");

        GiphyService service = new GiphyService(createBuilderMock(restClient), "k");
        assertThat(service.trending(20, 10)).isEqualTo("{\"data\":[]}");
    }

    @Test
    void search_returnsNullWhenNotConfigured() {
        GiphyService service = new GiphyService(createBuilderMock(restClient), "");
        assertThat(service.search("x", 1, 0)).isNull();
    }
}
