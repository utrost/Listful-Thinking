package app.listful.settings.dto;

import app.listful.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
    @NotBlank @Size(min = 3, max = 64) String username,
    @Email String email,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotNull UserRole role
) {
    @jakarta.validation.constraints.AssertTrue(message = "password must fit within 72 UTF-8 bytes")
    public boolean isPasswordLengthValid() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
