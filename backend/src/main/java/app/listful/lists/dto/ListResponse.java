package app.listful.lists.dto;

public record ListResponse(
    String id,
    String title,
    String description,
    String type,
    boolean publicList,
    String shareToken,
    String publicShareMode,
    String targetDate,
    String access,
    String createdAt
) {
}
