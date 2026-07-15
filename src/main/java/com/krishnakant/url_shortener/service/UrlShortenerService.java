package com.krishnakant.url_shortener.service;

import com.krishnakant.url_shortener.dto.UrlShortenResponse;
import com.krishnakant.url_shortener.entity.UrlMapping;
import com.krishnakant.url_shortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final UrlMappingRepository repository;

    @Transactional
    public UrlShortenResponse shortenUrl(String originalUrl, String alias, String baseUrl) {

        // Check if already exists (idempotent behavior)
        Optional<UrlMapping> existing = repository.findByOriginalUrl(originalUrl);

        if(existing.isPresent()) {
            return mapToResponse(existing.get(), baseUrl);
        }

        // TODO: write logic to convert originalURL to shortURL with the help of users alias
        // TODO: write logic to convert originalURL to shortURL using base62Encode

        UrlMapping urlMapping = new UrlMapping();

        return mapToResponse(urlMapping, baseUrl);

    }

    public String getOriginalUrl(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(urlMapping -> urlMapping.getOriginalUrl())
                .orElseThrow(() -> new RuntimeException("Short code not found: " + shortCode));
    }

    private UrlShortenResponse mapToResponse(UrlMapping urlMapping, String baseUrl) {
        return new UrlShortenResponse(
                urlMapping.getId(),
                urlMapping.getOriginalUrl(),
                urlMapping.getShortCode(),
                baseUrl + "/" + urlMapping.getShortCode(),
                urlMapping.getCreatedAt()
        );
    }
}
