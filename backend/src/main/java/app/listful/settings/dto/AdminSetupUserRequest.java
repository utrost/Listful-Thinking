package app.listful.settings.dto;

import app.listful.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminSetupUserRequest(
    @NotBlank @Size(min = 3, max = 64) String username,
    @Email String email,
    @NotNull UserRole role
) {}
