package app.listful.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailLinkRequest(@NotBlank @Email String email) {
}
