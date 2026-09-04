package app.listful.sharing.dto;

import java.util.List;

public record PublicListResponse(
    String title,
    String description,
    String type,
    String targetDate,
    String mode,
    List<PublicItemResponse> items
) {
}
