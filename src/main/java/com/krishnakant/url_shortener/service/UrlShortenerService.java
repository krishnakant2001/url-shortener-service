package com.krishnakant.url_shortener.service;

import com.krishnakant.url_shortener.dto.UrlShortenResponse;
import com.krishnakant.url_shortener.entity.UrlMapping;
import com.krishnakant.url_shortener.exception.AliasAlreadyExistsException;
import com.krishnakant.url_shortener.exception.UrlNotFoundException;
import com.krishnakant.url_shortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private static final int SHORT_CODE_LENGTH = 7;
    private static final int MAX_ATTEMPTS_TO_GENERATE_SHORT_CODE = 5;
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final UrlMappingRepository repository;
    private final SecureRandom random = new SecureRandom();


    @Transactional
    public UrlShortenResponse shortenUrl(String originalUrl, String alias, String baseUrl) {

        // Validate URL
        validateUrl(originalUrl);


        // Check if already exists (idempotent behavior)
        Optional<UrlMapping> existing = repository.findByOriginalUrl(originalUrl);

        if(existing.isPresent()) {
            return mapToResponse(existing.get(), baseUrl, "Already original url exists");
        }


        // Convert originalURL to shortURL with alias
        if(alias != null && !alias.isBlank()) {
            String normalizedAlias = alias.toLowerCase();

            if (repository.existsByShortCodeIgnoreCase(normalizedAlias)) {
                throw new AliasAlreadyExistsException(normalizedAlias);
            }

            UrlMapping urlMapping = new UrlMapping();
            urlMapping.setOriginalUrl(originalUrl);
            urlMapping.setShortCode(alias);
            UrlMapping savedMapping = repository.save(urlMapping);

            return mapToResponse(savedMapping, baseUrl, "Created successfully with alias");
        }


        // Convert originalURL to shortURL using Base62 random short code
        String shortCode = generateUniqueShortCode();

        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setOriginalUrl(originalUrl);
        urlMapping.setShortCode(shortCode);
        UrlMapping savedMapping = repository.save(urlMapping);

        return mapToResponse(savedMapping, baseUrl, "Created successfully with base62 random secure code");
    }

    public String getOriginalUrl(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(urlMapping -> urlMapping.getOriginalUrl())
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
    }


    // Retries up to maximum times to find a unique short code
    private String generateUniqueShortCode() {
        for(int attempt = 0; attempt < MAX_ATTEMPTS_TO_GENERATE_SHORT_CODE; attempt++) {
            String code = generateShortCode();
            if (!repository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new RuntimeException("Short code generation failed, please retry");
    }


    // Generates a random Base62 short code using SecureRandom
    private String generateShortCode() {
        StringBuilder sb = new StringBuilder(SHORT_CODE_LENGTH);
        for(int i = 0; i < SHORT_CODE_LENGTH; i++) {
            sb.append(BASE62.charAt(random.nextInt(BASE62.length())));
        }
        return sb.toString();
    }


    // Base62 Encoder
    private String base62Encode(long value) {
        StringBuilder sb = new StringBuilder();

        while (value > 0) {
            int remainder = (int) (value % 62);
            sb.append(BASE62.charAt(remainder));
            value /= 62;
        }

        return sb.reverse().toString();
    }


    // URL validator
    private void validateUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
            throw new IllegalArgumentException("Only http/https URLs are allowed.");
        }
    }


    // Response Mapping
    private UrlShortenResponse mapToResponse(UrlMapping urlMapping, String baseUrl, String message) {
        return new UrlShortenResponse(
                urlMapping.getId(),
                urlMapping.getOriginalUrl(),
                urlMapping.getShortCode(),
                baseUrl + "/" + urlMapping.getShortCode(),
                message,
                urlMapping.getCreatedAt()
        );
    }
}
