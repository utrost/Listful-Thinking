package app.listful.notifications.dto;

public record NotificationResponse(
    String id,
    String messageKey,
    String message,
    String readAt,
    String createdAt
) {
}
