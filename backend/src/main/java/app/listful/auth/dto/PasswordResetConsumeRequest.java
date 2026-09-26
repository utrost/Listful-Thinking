package app.listful.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConsumeRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8, max = 72) String password
) {
    @jakarta.validation.constraints.AssertTrue(message = "password must fit within 72 UTF-8 bytes")
    public boolean isPasswordLengthValid() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
