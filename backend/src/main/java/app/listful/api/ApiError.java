package app.listful.api;

public record ApiError(
    String code,
    String message
) {
}
