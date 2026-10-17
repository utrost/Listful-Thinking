package app.listful.sharing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestClaimControlRequest(
    @NotBlank @Size(min = 32, max = 100) String claimToken,
    @Size(max = 120) String guestName
) {
}
