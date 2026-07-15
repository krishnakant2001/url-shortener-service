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
        when(repository.findByShortCode(alias)).thenReturn(Optional.empty());
        when(repository.save(any(UrlMapping.class))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(ORIGINAL_URL, alias, BASE_URL);

        assertThat(response.getShortCode()).isEqualTo(alias);
        assertThat(response.getShortUrl()).isEqualTo(BASE_URL + "/" + alias);
        assertThat(response.getMessage()).isEqualTo("Created successfully with alias");
        verify(repository, never()).updateShortCode(anyLong(), anyString());
    }

    @Test
    void shortenUrl_aliasTaken_throwsAliasAlreadyExistsException() {
        String alias = "taken";
        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.empty());
        when(repository.findByShortCode(alias)).thenReturn(Optional.of(buildMapping(2L, "https://other.com", alias)));

        assertThatThrownBy(() -> service.shortenUrl(ORIGINAL_URL, alias, BASE_URL))
                .isInstanceOf(AliasAlreadyExistsException.class)
                .hasMessageContaining(alias);

        verify(repository, never()).save(any());
    }

    // ── shortenUrl: base62 encoding ───────────────────────────────────────

    @Test
    void shortenUrl_newUrlWithoutAlias_id1_generatesBase62Code() {
        UrlMapping saved = buildMapping(1L, ORIGINAL_URL, null);
        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.empty());
        when(repository.save(any(UrlMapping.class))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(ORIGINAL_URL, null, BASE_URL);

        assertThat(response.getShortCode()).isEqualTo("1");
        assertThat(response.getShortUrl()).isEqualTo(BASE_URL + "/1");
        assertThat(response.getMessage()).isEqualTo("Created successfully with base62 encoding");
        verify(repository).updateShortCode(1L, "1");
    }

    @Test
    void shortenUrl_newUrlWithoutAlias_id62_producesBase62Code10() {
        // 62 in base62 → "10"
        UrlMapping saved = buildMapping(62L, ORIGINAL_URL, null);
        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.empty());
        when(repository.save(any(UrlMapping.class))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(ORIGINAL_URL, null, BASE_URL);

        assertThat(response.getShortCode()).isEqualTo("10");
        verify(repository).updateShortCode(62L, "10");
    }

    @Test
    void shortenUrl_newUrlWithoutAlias_id3844_producesBase62Code100() {
        // 62^2 = 3844 → "100"
        UrlMapping saved = buildMapping(3844L, ORIGINAL_URL, null);
        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.empty());
        when(repository.save(any(UrlMapping.class))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(ORIGINAL_URL, null, BASE_URL);

        assertThat(response.getShortCode()).isEqualTo("100");
        verify(repository).updateShortCode(3844L, "100");
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

    // ── edge case: blank alias fall back to base 62 ─────────────────────────

    @Test
    void shortenUrl_blankAlias_treatedAsNoAlias_generateBase62Code() {
        UrlMapping saved = buildMapping(1L, ORIGINAL_URL, null);
        when(repository.findByOriginalUrl(ORIGINAL_URL)).thenReturn(Optional.empty());
        when(repository.save((any(UrlMapping.class)))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(ORIGINAL_URL, "    ", BASE_URL);

        assertThat(response.getShortCode()).isEqualTo("1");
        assertThat(response.getMessage()).isEqualTo("Created successfully with base62 encoding");
        verify(repository, never()).findByShortCode(anyString());
    }

    // ── edge case: http (not just https) is accepted ────────────────────────

    @Test
    void shortenUrl_httpUrl_isAccepted() {
        String httpUrl = "http://www.example.com/page";
        UrlMapping saved = buildMapping(2L, httpUrl, null);
        when(repository.findByOriginalUrl(httpUrl)).thenReturn(Optional.empty());
        when(repository.save((any(UrlMapping.class)))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(httpUrl, null, BASE_URL);

        assertThat(response.getOriginalUrl()).isEqualTo(httpUrl);
        assertThat(response.getMessage()).isEqualTo("Created successfully with base62 encoding");
    }

    // ── edge case: URL with query parameters ───────────────────────────────

    @Test
    void shortenUrl_urlWithQueryParams_isAccepted() {
        String urlWithParams = "http://www.example.com/search?q=hello+world&page=1";
        UrlMapping saved = buildMapping(3L, urlWithParams, null);
        when(repository.findByOriginalUrl(urlWithParams)).thenReturn(Optional.empty());
        when(repository.save((any(UrlMapping.class)))).thenReturn(saved);

        UrlShortenResponse response = service.shortenUrl(urlWithParams, null, BASE_URL);

        assertThat(response.getOriginalUrl()).isEqualTo(urlWithParams);
        assertThat(response.getMessage()).isEqualTo("Created successfully with base62 encoding");
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

