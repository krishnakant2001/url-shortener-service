package com.krishnakant.url_shortener.dto;

import lombok.Data;
import java.time.Instant;

@Data
public class UrlShortenResponse {

    private final Long id;
    private final String originalUrl;
    private final String shortCode;
    private final String shortUrl;
    private final String message;
    private final Instant createdAt;
}
