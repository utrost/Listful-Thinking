package app.listful.scraping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScrapeRequest(
    @NotBlank @Size(max = 2000) String url
) {
}
