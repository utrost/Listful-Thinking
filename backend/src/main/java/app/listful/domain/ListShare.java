package app.listful.domain;

import app.listful.domain.enums.ListSharePermission;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "list_shares")
public class ListShare {
    @EmbeddedId
    private ListShareId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("listId")
    @JoinColumn(name = "list_id", nullable = false)
    private ListEntity list;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    private ListSharePermission permission = ListSharePermission.READ;

    protected ListShare() {
    }

    public ListShare(ListEntity list, User user, Instant createdAt) {
        this(list, user, createdAt, ListSharePermission.READ);
    }

    public ListShare(ListEntity list, User user, Instant createdAt, ListSharePermission permission) {
        this.id = new ListShareId(list.getId(), user.getId());
        this.list = list;
        this.user = user;
        this.createdAt = createdAt;
        this.permission = permission == null ? ListSharePermission.READ : permission;
    }

    public ListShareId getId() { return id; }
    public ListEntity getList() { return list; }
    public User getUser() { return user; }
    public Instant getCreatedAt() { return createdAt; }
    public ListSharePermission getPermission() { return permission; }
}
