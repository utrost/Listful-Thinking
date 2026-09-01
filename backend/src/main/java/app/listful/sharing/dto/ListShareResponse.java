package app.listful.sharing.dto;

public record ListShareResponse(
    String listId,
    String userId,
    String username,
    String permission,
    String createdAt
) {
}
