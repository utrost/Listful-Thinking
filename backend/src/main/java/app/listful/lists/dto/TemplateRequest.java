package app.listful.lists.dto;
public record TemplateRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=255) String title, java.time.Instant targetDate) {}
