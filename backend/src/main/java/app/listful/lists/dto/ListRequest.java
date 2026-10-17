package app.listful.lists.dto;

import app.listful.domain.enums.ListType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.net.URI;
import jakarta.validation.constraints.AssertTrue;

public record ListRequest(
    @NotBlank @Size(max = 255) String title,
    @Size(max = 2000) String description,
    @NotNull ListType type,
    Instant targetDate,
    @Size(max = 2000) String sourceUrl,
    @Size(max = 255) String eventLocation
) {
    public ListRequest(String title, String description, ListType type, Instant targetDate) {
        this(title, description, type, targetDate, null, null);
    }

    @AssertTrue(message = "sourceUrl must be an HTTP(S) URL")
    public boolean isSourceUrlValid() {
        if (sourceUrl == null || sourceUrl.isBlank()) return true;
        try {
            URI uri = URI.create(sourceUrl);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
