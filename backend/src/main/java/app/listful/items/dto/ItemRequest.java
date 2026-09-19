package app.listful.items.dto;

import app.listful.domain.enums.ItemStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

public record ItemRequest(
    @Size(max = 255) String name,
    @Size(max = 2000) String description,
    @Size(max = 2000) String url,
    String imageUrl,
    BigDecimal price,
    ItemStatus status,
    Instant dueDate,
    @Size(max = 255) String recurrenceRule,
    @Size(max = 255) String quantity,
    @Size(max = 255) String category,
    @Size(max = 255) String ownerLabel,
    @Size(max = 255) String assistantLabels
) {
    public static final int MAX_IMAGE_URL_LENGTH = 2_000;
    public static final int MAX_IMAGE_DATA_URL_LENGTH = 5_000_000;
    private static final Set<String> ALLOWED_IMAGE_DATA_MIME_TYPES = Set.of(
        "image/png", "image/jpeg", "image/webp", "image/gif"
    );

    @AssertTrue(message = "imageUrl must be an HTTP(S) URL up to 2000 characters or a supported image data URL")
    public boolean isImageUrlValid() {
        if (imageUrl == null || imageUrl.isBlank()) {
            return true;
        }
        if (imageUrl.startsWith("data:")) {
            return isValidImageDataUrl(imageUrl);
        }
        if (imageUrl.length() > MAX_IMAGE_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(imageUrl);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isValidImageDataUrl(String value) {
        if (value.length() > MAX_IMAGE_DATA_URL_LENGTH) {
            return false;
        }
        int separator = value.indexOf(";base64,");
        if (separator < 5 || !ALLOWED_IMAGE_DATA_MIME_TYPES.contains(value.substring(5, separator))) {
            return false;
        }
        String encoded = value.substring(separator + ";base64,".length());
        if (encoded.isEmpty()) {
            return false;
        }
        try {
            Base64.getDecoder().decode(encoded);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
