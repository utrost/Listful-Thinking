package app.listful.domain;

import app.listful.domain.enums.ListType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lists")
public class ListEntity {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListType type;

    @Column(name = "share_token", unique = true)
    private String shareToken;

    @Column(name = "is_public", nullable = false)
    private int publicFlag;

    @Column(name = "target_date")
    private Instant targetDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ListEntity() {
    }

    public ListEntity(User user, String title, String description, ListType type, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.title = title;
        this.description = description;
        this.type = type;
        this.createdAt = createdAt;
        this.publicFlag = 0;
    }

    public void enablePublicShare(String shareToken) {
        this.shareToken = shareToken;
        this.publicFlag = 1;
    }

    public void disablePublicShare() {
        this.shareToken = null;
        this.publicFlag = 0;
    }

    public void update(String title, String description, ListType type, Instant targetDate) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.targetDate = targetDate;
    }

    public String getId() { return id; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ListType getType() { return type; }
    public String getShareToken() { return shareToken; }
    public boolean isPublicList() { return publicFlag == 1; }
    public Instant getTargetDate() { return targetDate; }
    public Instant getCreatedAt() { return createdAt; }
}
