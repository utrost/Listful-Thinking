package app.listful.scraping.dto;

import java.math.BigDecimal;

public record ScrapeResponse(
    String title,
    String description,
    String imageUrl,
    BigDecimal price
) {
}
