package app.listful.settings.dto;

public record AdminSetupLinkResponse(
    AdminUserResponse user,
    String setupUrl,
    String expiresAt,
    boolean emailed
) {}
