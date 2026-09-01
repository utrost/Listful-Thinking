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
    String reservedByGuest
) {
}
