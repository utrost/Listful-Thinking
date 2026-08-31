package app.listful.lists.dto;

import app.listful.domain.enums.ListType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ListRequest(
    @NotBlank @Size(max = 255) String title,
    @Size(max = 2000) String description,
    @NotNull ListType type,
    Instant targetDate
) {
}
