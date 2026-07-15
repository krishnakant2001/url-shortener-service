package com.krishnakant.url_shortener.controller;

import com.krishnakant.url_shortener.dto.UrlShortenRequest;
import com.krishnakant.url_shortener.dto.UrlShortenResponse;
import com.krishnakant.url_shortener.exception.AliasAlreadyExistsException;
import com.krishnakant.url_shortener.exception.UrlNotFoundException;
import com.krishnakant.url_shortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UrlShortenerController.class)
class UrlShortenerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UrlShortenerService service;

    private static final String ORIGINAL_URL = "https://www.example.com/some/long/path";

    // ── helpers ──────────────────────────────────────────────────────────────

    private UrlShortenRequest buildRequest(String url, String alias) {
        UrlShortenRequest req = new UrlShortenRequest();
        req.setUrl(url);
        req.setAlias(alias);
        return req;
    }

    private UrlShortenResponse buildResponse(String shortCode, String message) {
        return new UrlShortenResponse(
                1L,
                ORIGINAL_URL,
                shortCode,
                "http://localhost/" + shortCode,
                message,
                Instant.now()
        );
    }

    // ── POST /shorten ─────────────────────────────────────────────────────

    @Test
    void shorten_validUrlNoAlias_returns201WithResponse() throws Exception {
        when(service.shortenUrl(eq(ORIGINAL_URL), isNull(), anyString()))
                .thenReturn(buildResponse("1", "Created successfully with base62 encoding"));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(ORIGINAL_URL, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("1"))
                .andExpect(jsonPath("$.originalUrl").value(ORIGINAL_URL))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost/1"))
                .andExpect(jsonPath("$.message").value("Created successfully with base62 encoding"));
    }

    @Test
    void shorten_validUrlWithAlias_returns201WithAlias() throws Exception {
        when(service.shortenUrl(eq(ORIGINAL_URL), eq("my-alias"), anyString()))
                .thenReturn(buildResponse("my-alias", "Created successfully with alias"));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(ORIGINAL_URL, "my-alias"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("my-alias"))
                .andExpect(jsonPath("$.message").value("Created successfully with alias"));
    }

    @Test
    void shorten_existingUrl_returns201WithExistingMapping() throws Exception {
        when(service.shortenUrl(eq(ORIGINAL_URL), isNull(), anyString()))
                .thenReturn(buildResponse("1", "Already original url exists"));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(ORIGINAL_URL, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Already original url exists"));
    }

    // ── POST /shorten – validation errors (400) ────────────────────────────

    @Test
    void shorten_missingUrl_returns400() throws Exception {
        // url field absent → null → @NotBlank fails
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    void shorten_noRequestBody_returns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shorten_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("   ", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    void shorten_aliasTooShort_returns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(ORIGINAL_URL, "ab"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alias").exists());
    }

    @Test
    void shorten_aliasTooLong_returns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(ORIGINAL_URL, "a".repeat(21)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alias").exists());
    }

    @Test
    void shorten_aliasWithInvalidChars_returns400() throws Exception {
        // spaces and special chars not in [a-zA-Z0-9_-]
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(ORIGINAL_URL, "my alias!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.alias").exists());
    }

    // ── POST /shorten – service-level errors ──────────────────────────────

    @Test
    void shorten_invalidUrlScheme_returns400() throws Exception {
        when(service.shortenUrl(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Only http/https URLs are allowed."));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("ftp://example.com", null))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Only http/https URLs are allowed."));
    }

    @Test
    void shorten_aliasTaken_returns409() throws Exception {
        when(service.shortenUrl(any(), any(), any()))
                .thenThrow(new AliasAlreadyExistsException("taken"));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest(ORIGINAL_URL, "taken"))))
                .andExpect(status().isConflict())
                .andExpect(content().string("Alias already taken: taken"));
    }

    // ── GET /{code} ───────────────────────────────────────────────────────

    @Test
    void redirect_existingCode_returns301WithLocationHeader() throws Exception {
        when(service.getOriginalUrl("abc123")).thenReturn(ORIGINAL_URL);

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", ORIGINAL_URL));
    }

    @Test
    void redirect_nonExistentCode_returns404() throws Exception {
        when(service.getOriginalUrl("xyz")).thenThrow(new UrlNotFoundException("xyz"));

        mockMvc.perform(get("/xyz"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("No URL found for short code: xyz"));
    }
}

