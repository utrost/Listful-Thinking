package app.listful.scraping.dto;

import java.math.BigDecimal;
import java.util.List;

/** A proposal for the user to review; extraction never writes list data. */
public record PageDraftResponse(
    String kind,
    String sourceUrl,
    String extractedAt,
    String title,
    String description,
    String imageUrl,
    BigDecimal price,
    String priceCurrency,
    String seller,
    String availability,
    List<String> ingredients,
    List<String> steps,
    String eventStart,
    String eventLocation,
    String address,
    Double latitude,
    Double longitude
) {
}
