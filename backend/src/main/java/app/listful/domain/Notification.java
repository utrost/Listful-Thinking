package app.listful.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(name = "message_args")
    private String messageArgs;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(User user, String messageKey, String messageArgs, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
        this.createdAt = createdAt;
    }

    public void markRead(Instant readAt) {
        this.readAt = readAt;
    }

    public String getId() { return id; }
    public User getUser() { return user; }
    public String getMessageKey() { return messageKey; }
    public String getMessageArgs() { return messageArgs; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
