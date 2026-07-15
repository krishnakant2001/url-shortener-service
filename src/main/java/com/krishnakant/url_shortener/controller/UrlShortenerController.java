package com.krishnakant.url_shortener.controller;

import com.krishnakant.url_shortener.dto.UrlShortenRequest;
import com.krishnakant.url_shortener.dto.UrlShortenResponse;
import com.krishnakant.url_shortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UrlShortenerController {

    private final UrlShortenerService service;

    @PostMapping("/shorten")
    public ResponseEntity<UrlShortenResponse> shorten(
            @Valid @RequestBody UrlShortenRequest request, HttpServletRequest httpServletRequest) {

        String baseUrl = getBaseUrl(httpServletRequest);

        // TODO: Call service to do url short
        UrlShortenResponse response = null;

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {

        // TODO: Call service to get original Url
        String originalUrl = null;

        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, originalUrl)
                .build();
    }



    // Base URL helper
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host   = request.getServerName();
        int port      = request.getServerPort();

        // Omit port for standard ports (80 for http, 443 for https)
        if ((scheme.equals("http") && port == 80) ||
                (scheme.equals("https") && port == 443)) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

}
