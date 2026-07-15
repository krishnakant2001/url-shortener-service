package com.krishnakant.url_shortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UrlShortenRequest {

    @NotBlank(message = "URL must not be blank")
    private String url;

    // user can provide custom alias
    @Size(min = 3, max = 20, message = "Alias must be between 3 and 20 characters")
    @Pattern(
            regexp = "^[a-zA-Z0-9_-]+$",
            message = "Alias can only contain letters, digits, hyphens, and underscores"
    )
    private String alias;
}
