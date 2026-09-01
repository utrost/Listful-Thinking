package app.listful.settings.dto;

import jakarta.validation.constraints.NotNull;

public record AdminUpdateUserRequest(@NotNull Boolean active) {
}
