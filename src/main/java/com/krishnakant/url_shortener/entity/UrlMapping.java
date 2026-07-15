package com.krishnakant.url_shortener.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "url_mapping",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_url_mapping_original_url", columnNames = "original_url"),
                @UniqueConstraint(name = "uq_url_mapping_short_code",   columnNames = "short_code")
        }
)
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "short_code", updatable = false)
    private String shortCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void prePersist() {
        this.createdAt = Instant.now();
    }
}