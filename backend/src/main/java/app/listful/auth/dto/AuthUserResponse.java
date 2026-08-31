package app.listful.auth.dto;

public record AuthUserResponse(
    String id,
    String username,
    String email,
    String role
) {
}
