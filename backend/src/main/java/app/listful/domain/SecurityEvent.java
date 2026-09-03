package app.listful.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_events")
public class SecurityEvent {
    @Id
    private String id;

    @Column(nullable = false)
    private String type;

    @Column(name = "actor_id")
    private String actorId;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "path")
    private String path;

    @Column(name = "details", length = 2000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SecurityEvent() {}

    public SecurityEvent(String type, String actorId, String clientIp, String path, String details, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.actorId = actorId;
        this.clientIp = clientIp;
        this.path = path;
        this.details = details;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getActorId() { return actorId; }
    public String getClientIp() { return clientIp; }
    public String getPath() { return path; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
