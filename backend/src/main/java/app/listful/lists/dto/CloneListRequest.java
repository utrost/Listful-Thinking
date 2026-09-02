package app.listful.lists.dto;

import jakarta.validation.constraints.Size;

public record CloneListRequest(
    @Size(max = 255) String title
) {
}
