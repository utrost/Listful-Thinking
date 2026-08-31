package app.listful.items.dto;

import app.listful.domain.enums.ItemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record ItemRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 2000) String url,
    @Size(max = 2000) String imageUrl,
    BigDecimal price,
    ItemStatus status,
    Instant dueDate,
    @Size(max = 255) String recurrenceRule
) {
}
