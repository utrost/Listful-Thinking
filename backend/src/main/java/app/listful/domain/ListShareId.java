package app.listful.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ListShareId implements Serializable {
    @Column(name = "list_id")
    private String listId;

    @Column(name = "user_id")
    private String userId;

    protected ListShareId() {
    }

    public ListShareId(String listId, String userId) {
        this.listId = listId;
        this.userId = userId;
    }

    public String getListId() { return listId; }
    public String getUserId() { return userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ListShareId that)) return false;
        return Objects.equals(listId, that.listId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listId, userId);
    }
}
