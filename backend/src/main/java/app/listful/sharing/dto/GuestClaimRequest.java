package app.listful.sharing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestClaimRequest(
    @NotBlank @Size(max = 255) String guestName
) {
}
