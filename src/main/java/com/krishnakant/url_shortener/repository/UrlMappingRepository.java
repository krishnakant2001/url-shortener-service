package com.krishnakant.url_shortener.repository;

import com.krishnakant.url_shortener.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    /**
     * Hot path — every redirect hits this.
     * Backed by idx_url_mapping_short_code (unique index)
     */
    Optional<UrlMapping> findByShortCode(String shortCode);


    /**
     * Before inserting, check if this URL was already shortened.
     * If present → return existing mapping (idempotent behaviour).
     */
    Optional<UrlMapping> findByOriginalUrl(String originalUrl);
}
