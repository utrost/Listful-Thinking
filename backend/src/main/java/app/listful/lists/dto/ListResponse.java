package app.listful.lists.dto;

public record ListResponse(
    String id,
    String title,
    String description,
    String type,
    boolean publicList,
    String shareToken,
    String targetDate,
    String createdAt
) {
}
