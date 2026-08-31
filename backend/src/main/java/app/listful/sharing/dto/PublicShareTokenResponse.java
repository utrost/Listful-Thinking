package app.listful.sharing.dto;

public record PublicShareTokenResponse(
    String listId,
    boolean publicList,
    String shareToken,
    String shareUrl
) {
}
