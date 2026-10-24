package app.listful.sharing.dto;

import java.math.BigDecimal;

public record PublicItemResponse(
    String id,
    String name,
    String description,
    String url,
    String imageUrl,
    BigDecimal price,
    String status,
    String dueDate,
    String quantity,
    String category,
    String reservedByGuest,
    String priceCurrency,
    String variantNote,
    String seller,
    String availability,
    String priceCheckedAt,
    String sourceUrl,
    String claimToken,
    String address,
    Double latitude,
    Double longitude
) {
    public PublicItemResponse withClaimToken(String token) {
        return new PublicItemResponse(id, name, description, url, imageUrl, price, status, dueDate,
            quantity, category, reservedByGuest, priceCurrency, variantNote, seller, availability,
            priceCheckedAt, sourceUrl, token, address, latitude, longitude);
    }
}
