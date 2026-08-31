package app.listful.items.dto;

import java.math.BigDecimal;

public record ItemResponse(
    String id,
    String listId,
    String name,
    String url,
    String imageUrl,
    BigDecimal price,
    String status,
    String dueDate,
    String recurrenceRule,
    String reservedByGuest
) {
}
