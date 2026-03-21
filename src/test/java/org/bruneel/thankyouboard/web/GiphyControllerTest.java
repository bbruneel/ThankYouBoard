package org.bruneel.thankyouboard.web;

import org.bruneel.thankyouboard.security.SecurityConfig;
import org.bruneel.thankyouboard.service.GiphyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GiphyController.class)
@Import(SecurityConfig.class)
class GiphyControllerTest {

    private static final String ACCEPT_VERSION_1 = "application/json;version=1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GiphyService giphyService;

    @Test
    void search_returns503WhenNotConfigured() throws Exception {
        given(giphyService.isConfigured()).willReturn(false);

        mockMvc.perform(get("/api/giphy/search").accept(ACCEPT_VERSION_1))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Giphy API key not configured. Set giphy.api-key or GIPHY_API_KEY."));
    }

    @Test
    void search_returnsOkWithBodyWhenConfigured() throws Exception {
        given(giphyService.isConfigured()).willReturn(true);
        given(giphyService.search("cats", 10, 0)).willReturn("{\"data\":[{\"id\":\"1\"}],\"pagination\":{\"total_count\":1,\"count\":1,\"offset\":0}}");

        mockMvc.perform(get("/api/giphy/search")
                        .param("q", "cats")
                        .param("limit", "10")
                        .param("offset", "0")
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data[0].id").value("1"));
    }

    @Test
    void search_usesDefaultParams() throws Exception {
        given(giphyService.isConfigured()).willReturn(true);
        given(giphyService.search("", 10, 0)).willReturn("{\"data\":[],\"pagination\":{\"total_count\":0,\"count\":0,\"offset\":0}}");

        mockMvc.perform(get("/api/giphy/search").accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk());
    }

    @Test
    void search_returnsEmptyJsonWhenServiceReturnsNull() throws Exception {
        given(giphyService.isConfigured()).willReturn(true);
        given(giphyService.search("x", 10, 0)).willReturn(null);

        mockMvc.perform(get("/api/giphy/search").param("q", "x").accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.total_count").value(0));
    }

    @Test
    void trending_returns503WhenNotConfigured() throws Exception {
        given(giphyService.isConfigured()).willReturn(false);

        mockMvc.perform(get("/api/giphy/trending").accept(ACCEPT_VERSION_1))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Giphy API key not configured. Set giphy.api-key or GIPHY_API_KEY."));
    }

    @Test
    void trending_returnsOkWithBodyWhenConfigured() throws Exception {
        given(giphyService.isConfigured()).willReturn(true);
        given(giphyService.trending(5, 0)).willReturn("{\"data\":[{\"id\":\"gif1\"}],\"pagination\":{\"total_count\":1,\"count\":1,\"offset\":0}}");

        mockMvc.perform(get("/api/giphy/trending")
                        .param("limit", "5")
                        .param("offset", "0")
                        .accept(ACCEPT_VERSION_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("gif1"));
    }
}
