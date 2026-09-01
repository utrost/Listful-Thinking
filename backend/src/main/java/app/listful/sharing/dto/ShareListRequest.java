package app.listful.sharing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShareListRequest(
    @NotBlank @Size(max = 255) String username,
    String permission
) {
}
