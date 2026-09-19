package app.listful.scraping.dto;

import java.math.BigDecimal;

public record ScrapeResponse(
    String title,
    String description,
    String imageUrl,
    BigDecimal price,
    String priceCurrency
) {
    public ScrapeResponse(String title, String description, String imageUrl, BigDecimal price) {
        this(title, description, imageUrl, price, null);
    }
}
