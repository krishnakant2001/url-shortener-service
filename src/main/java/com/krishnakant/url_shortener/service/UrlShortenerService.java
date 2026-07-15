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


        // Convert originalURL to shortURL with alias
        if(alias != null && !alias.isBlank()) {
            if (repository.findByShortCode(alias).isPresent()) {
                throw new RuntimeException("Alias already taken: " + alias);
            }
        }

        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setOriginalUrl(originalUrl);
        urlMapping.setShortCode(alias);
        UrlMapping savedMapping = repository.save(urlMapping);

        return mapToResponse(savedMapping, baseUrl);

        // TODO: write logic to convert originalURL to shortURL using base62Encode

    }

    public String getOriginalUrl(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(urlMapping -> urlMapping.getOriginalUrl())
                .orElseThrow(() -> new RuntimeException("Short code not found: " + shortCode));
    }


    // Response Mapping
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
