package app.listful.settings.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAdminSettingsRequest(
    @NotNull Boolean registrationEnabled
) {
}
