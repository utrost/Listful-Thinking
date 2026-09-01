package app.listful.items.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PostponeRequest(
    @Min(1) @Max(365) int days
) {
}
