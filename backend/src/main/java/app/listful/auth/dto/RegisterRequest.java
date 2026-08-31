package app.listful.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(min = 3, max = 80) String username,
    @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 255) String password
) {
}
