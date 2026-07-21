package com.krishnakant.url_shortener.repository;

import com.krishnakant.url_shortener.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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


    /**
     * Lightweight existence check, avoids fetching the full entity
     */
    boolean existsByShortCode(String code);


    /**
     * Lightweight existence check with case-insensitive
     * Avoids fetching the full entity
     */
    boolean existsByShortCodeIgnoreCase(String shortCode);


    /**
     * Called after INSERT to write the base62(id) short code back.
     * Runs as part of the same @Transactional in the service.
     */
    @Modifying
    @Query("UPDATE UrlMapping u SET u.shortCode = :shortCode WHERE u.id = :id")
    void updateShortCode(@Param("id") Long id, @Param("shortCode") String shortCode);
}
