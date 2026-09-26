package app.listful.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailLinkRequest(@NotBlank @Email String email, @jakarta.validation.constraints.Size(max = 255) String username) {
    public EmailLinkRequest {
        email = email == null ? null : email.trim();
        username = username == null ? null : username.trim();
    }
}
