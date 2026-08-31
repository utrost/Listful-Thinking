package app.listful.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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

    protected ListShare() {
    }

    public ListShare(ListEntity list, User user, Instant createdAt) {
        this.id = new ListShareId(list.getId(), user.getId());
        this.list = list;
        this.user = user;
        this.createdAt = createdAt;
    }

    public ListShareId getId() { return id; }
    public ListEntity getList() { return list; }
    public User getUser() { return user; }
    public Instant getCreatedAt() { return createdAt; }
}
