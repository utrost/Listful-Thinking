package app.listful.domain;

import app.listful.domain.enums.ListType;
import app.listful.domain.enums.PublicShareMode;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "public_share_mode", nullable = false)
    private PublicShareMode publicShareMode = PublicShareMode.VIEW;

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
        this.publicShareMode = defaultModeForType(type);
        this.createdAt = createdAt;
        this.publicFlag = 0;
    }

    public void enablePublicShare(String shareToken) {
        enablePublicShare(shareToken, defaultModeForType(type));
    }

    public void enablePublicShare(String shareToken, PublicShareMode mode) {
        this.shareToken = shareToken;
        this.publicShareMode = mode == null ? defaultModeForType(type) : mode;
        this.publicFlag = 1;
    }

    public void disablePublicShare() {
        this.shareToken = null;
        this.publicFlag = 0;
        this.publicShareMode = defaultModeForType(type);
    }

    public void update(String title, String description, ListType type, Instant targetDate) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.targetDate = targetDate;
        reconcilePublicShareMode();
    }

    public String getId() { return id; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ListType getType() { return type; }
    public String getShareToken() { return shareToken; }
    public boolean isPublicList() { return publicFlag == 1; }
    public PublicShareMode getPublicShareMode() { return publicShareMode == null ? defaultModeForType(type) : publicShareMode; }
    public Instant getTargetDate() { return targetDate; }
    public Instant getCreatedAt() { return createdAt; }

    private void reconcilePublicShareMode() {
        if (type == ListType.WISH && publicShareMode == PublicShareMode.SIGNUP) {
            publicShareMode = PublicShareMode.WISH_CLAIM;
        }
        if (type != ListType.WISH && publicShareMode == PublicShareMode.WISH_CLAIM) {
            publicShareMode = PublicShareMode.VIEW;
        }
    }

    private static PublicShareMode defaultModeForType(ListType type) {
        return type == ListType.WISH ? PublicShareMode.WISH_CLAIM : PublicShareMode.VIEW;
    }
}
