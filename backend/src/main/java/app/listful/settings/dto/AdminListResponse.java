package app.listful.settings.dto;

import java.time.Instant;

public record AdminListResponse(
    String id,
    String title,
    String description,
    String type,
    boolean publicList,
    String ownerId,
    String ownerUsername,
    String ownerEmail,
    Instant targetDate,
    Instant createdAt
) {
}
