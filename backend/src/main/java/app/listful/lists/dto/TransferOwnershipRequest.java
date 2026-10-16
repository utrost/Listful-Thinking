package app.listful.lists.dto;

import jakarta.validation.constraints.NotBlank;

public record TransferOwnershipRequest(@NotBlank String username) {}
