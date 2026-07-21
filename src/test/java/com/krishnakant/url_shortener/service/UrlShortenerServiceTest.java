package com.krishnakant.url_shortener.service;

import com.krishnakant.url_shortener.dto.UrlShortenResponse;
import com.krishnakant.url_shortener.entity.UrlMapping;
import com.krishnakant.url_shortener.exception.AliasAlreadyExistsException;
import com.krishnakant.url_shortener.exception.UrlNotFoundException;
import com.krishnakant.url_shortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock
    private UrlMappingRepository repository;

    @InjectMocks
    private UrlShortenerService service;

    private static final String BASE_URL     = "http://localhost:8080";
    private static final String ORIGINAL_URL = "https://www.example.com/some/long/path";

    // ── helpers ──────────────────────────────────────────────────────────────

    private UrlMapping buildMapping(Long id, String originalUrl, String shortCode) {
        UrlMapping m = new UrlMapping();
        m.setId(id);
        m.setOriginalUrl(originalUrl);
        m.setShortCode(shortCode);
        m.setCreatedAt(Instant.now());
        return m;
    }

    // ── shortenUrl: idempotent (URL already exists) ───────────────────────

    @Test
    void shortenUrl_existingUrl_returnsExistingMappingWithoutSaving() {
        UrlMapping existing = buildMapping(1L, ORIGINAL_URL, "1");
        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.of(existing));

        UrlShortenResponse response = service.shortenUrl(ORIGINAL_URL, null, BASE_URL);

        assertThat(response.getShortCode()).isEqualTo("1");
        assertThat(response.getOriginalUrl()).isEqualTo(ORIGINAL_URL);
        assertThat(response.getShortUrl()).isEqualTo(BASE_URL + "/1");
        assertThat(response.getMessage()).isEqualTo("Already original url exists");
        verify(repository, never()).save(any());
    }

    // ── shortenUrl: custom alias ──────────────────────────────────────────

    @Test
    void shortenUrl_newUrlWithAlias_savesWithAlias() {
        String alias  = "my-alias";
        UrlMapping saved = buildMapping(5L, ORIGINAL_URL, alias);

        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.empty());
        when(repository.existsByShortCodeIgnoreCase(alias.toLowerCase())).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(ORIGINAL_URL, alias, BASE_URL);

        assertThat(response.getShortCode()).isEqualTo(alias);
        assertThat(response.getShortUrl()).isEqualTo(BASE_URL + "/" + alias);
        assertThat(response.getMessage()).isEqualTo("Created successfully with alias");
        verify(repository, never()).updateShortCode(anyLong(), anyString());
        verify(repository).existsByShortCodeIgnoreCase(alias.toLowerCase());
    }

    @Test
    void shortenUrl_aliasTaken_throwsAliasAlreadyExistsException() {
        String alias = "taken";
        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.empty());
        when(repository.existsByShortCodeIgnoreCase(alias.toLowerCase())).thenReturn(true);

        assertThatThrownBy(() -> service.shortenUrl(ORIGINAL_URL, alias, BASE_URL))
                .isInstanceOf(AliasAlreadyExistsException.class)
                .hasMessageContaining(alias);

        verify(repository, never()).save(any());
    }

    // ── shortenUrl: SecureRandom short code generation ───────────────────────────────────────

    @Test
    void shortenUrl_newUrlWithoutAlias_generatesRandomShortCode() {
        String generateCode = "abc1234";
        UrlMapping saved = buildMapping(1L, ORIGINAL_URL, generateCode);

        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.empty());
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(ORIGINAL_URL, null, BASE_URL);

        assertThat(response.getShortCode()).isEqualTo(generateCode);
        assertThat(response.getShortUrl()).isEqualTo(BASE_URL + "/" + generateCode);
        assertThat(response.getMessage()).isEqualTo("Created successfully with base62 random secure code");
        verify(repository, never()).updateShortCode(anyLong(), anyString());
        verify(repository).existsByShortCode(anyString());
    }

    @Test
    void shortenUrl_shortCodeCollision_retriesAndSucceeds() {
        String freeCode = "free123";
        UrlMapping saved = buildMapping(62L, ORIGINAL_URL, freeCode);
        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.empty());
        when(repository.existsByShortCode(anyString()))
                .thenReturn(true)
                .thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(ORIGINAL_URL, null, BASE_URL);

        assertThat(response.getMessage()).isEqualTo("Created successfully with base62 random secure code");
        verify(repository, never()).updateShortCode(anyLong(), anyString());
    }

    @Test
    void shortenUrl_allAttemptExhausted_throwsRuntimeException() {
        // All 5 attempts produce a collision
        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.empty());
        when(repository.existsByShortCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.shortenUrl(ORIGINAL_URL, null, BASE_URL))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Short code generation failed, please retry");

        verify(repository, never()).save(any());
    }

    // ── shortenUrl: URL validation ─────────────────────────────────────────

    @Test
    void shortenUrl_ftpScheme_throwsIllegalArgumentExceptionWithSchemeMessage() {
        assertThatThrownBy(() -> service.shortenUrl("ftp://example.com", null, BASE_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only http/https URLs are allowed.");
    }

    @Test
    void shortenUrl_noScheme_throwsIllegalArgumentExceptionWithSchemeMessage() {
        assertThatThrownBy(() -> service.shortenUrl("/relative/path", null, BASE_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only http/https URLs are allowed.");
    }

    @Test
    void shortenUrl_malformedUrl_throwsIllegalArgumentExceptionWithInvalidUrlMessage() {
        assertThatThrownBy(() -> service.shortenUrl("not a url %%", null, BASE_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid URL");
    }

    // ── edge case: blank alias fall back to random SecureRandom code ─────────────────────────

    @Test
    void shortenUrl_blankAlias_treatedAsNoAlias_generateRandomShortCode() {
        String generatedCode = "xyz9876";
        UrlMapping saved = buildMapping(1L, ORIGINAL_URL, generatedCode);
        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.empty());
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(repository.save((any(UrlMapping.class)))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(ORIGINAL_URL, "    ", BASE_URL);

        assertThat(response.getShortCode()).isEqualTo(generatedCode);
        assertThat(response.getMessage()).isEqualTo("Created successfully with base62 random secure code");
        verify(repository, never()).findByShortCode(anyString());
        verify(repository, never()).existsByShortCodeIgnoreCase(anyString());
    }

    // ── edge case: http (not just https) is accepted ────────────────────────

    @Test
    void shortenUrl_httpUrl_isAccepted() {
        String httpUrl = "http://www.example.com/page";
        String generatedCode = "httpCode";
        UrlMapping saved = buildMapping(2L, httpUrl, generatedCode);
        when(repository.findByOriginalUrl(httpUrl)).thenReturn(Optional.empty());
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(repository.save((any(UrlMapping.class)))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(httpUrl, null, BASE_URL);

        assertThat(response.getOriginalUrl()).isEqualTo(httpUrl);
        assertThat(response.getMessage()).isEqualTo("Created successfully with base62 random secure code");
    }

    // ── edge case: URL with query parameters ───────────────────────────────

    @Test
    void shortenUrl_urlWithQueryParams_isAccepted() {
        String urlWithParams = "http://www.example.com/search?q=hello+world&page=1";
        String generatedCode = "qryCode1";
        UrlMapping saved = buildMapping(3L, urlWithParams, generatedCode);
        when(repository.findByOriginalUrl(urlWithParams)).thenReturn(Optional.empty());
        when(repository.existsByShortCode(anyString())).thenReturn(false);
        when(repository.save((any(UrlMapping.class)))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(urlWithParams, null, BASE_URL);

        assertThat(response.getOriginalUrl()).isEqualTo(urlWithParams);
        assertThat(response.getMessage()).isEqualTo("Created successfully with base62 random secure code");
    }

    // ── getOriginalUrl ─────────────────────────────────────────────────────

    @Test
    void getOriginalUrl_existingCode_returnsOriginalUrl() {
        UrlMapping mapping = buildMapping(1L, ORIGINAL_URL, "abc123");
        when(repository.findByShortCode("abc123")).thenReturn(Optional.of(mapping));

        String result = service.getOriginalUrl("abc123");

        assertThat(result).isEqualTo(ORIGINAL_URL);
    }

    @Test
    void getOriginalUrl_nonExistentCode_throwsUrlNotFoundException() {
        when(repository.findByShortCode("xyz")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOriginalUrl("xyz"))
                .isInstanceOf(UrlNotFoundException.class)
                .hasMessageContaining("xyz");
    }
}

