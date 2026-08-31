package app.listful.domain;

import app.listful.domain.enums.ItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "items")
public class Item {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private ListEntity list;

    @Column(nullable = false)
    private String name;

    private String url;

    @Column(name = "image_url")
    private String imageUrl;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "recurrence_rule")
    private String recurrenceRule;

    @Column(name = "reserved_by_guest")
    private String reservedByGuest;

    protected Item() {
    }

    public Item(ListEntity list, String name, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.list = list;
        this.name = name;
        this.status = ItemStatus.OPEN;
    }

    public void claimForGuest(String guestName) {
        this.status = ItemStatus.CLAIMED;
        this.reservedByGuest = guestName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void update(String name, String url, String imageUrl, BigDecimal price, ItemStatus status, Instant dueDate, String recurrenceRule) {
        this.name = name;
        this.url = url;
        this.imageUrl = imageUrl;
        this.price = price;
        this.status = status == null ? ItemStatus.OPEN : status;
        this.dueDate = dueDate;
        this.recurrenceRule = recurrenceRule;
    }

    public String getId() { return id; }
    public ListEntity getList() { return list; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public ItemStatus getStatus() { return status; }
    public Instant getDueDate() { return dueDate; }
    public void setDueDate(Instant dueDate) { this.dueDate = dueDate; }
    public String getRecurrenceRule() { return recurrenceRule; }
    public void setRecurrenceRule(String recurrenceRule) { this.recurrenceRule = recurrenceRule; }
    public String getReservedByGuest() { return reservedByGuest; }
}
