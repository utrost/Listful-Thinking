package app.listful.auth.dto;

public record AuthSettingsResponse(
    boolean registrationAvailable, boolean emailRecoveryAvailable,
    boolean bootstrapRequired, boolean bootstrapConfigured
) {
}
