package app.listful.sharing.dto;

import app.listful.domain.enums.PublicShareMode;

public record PublicShareRequest(
    PublicShareMode mode
) {
}
