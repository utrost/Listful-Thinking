package app.listful.settings.dto;

/** Instance usage only; administration does not grant access to private list content. */
public record AdminListResponse(String ownerId, String ownerUsername, long listCount) {}
