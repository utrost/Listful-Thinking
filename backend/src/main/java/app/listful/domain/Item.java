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

    private String description;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "recurrence_rule")
    private String recurrenceRule;

    private String quantity;

    private String category;

    @Column(name = "reserved_by_guest")
    private String reservedByGuest;

    @Column(name = "last_completed_at")
    private Instant lastCompletedAt;

    @Column(name = "owner_label")
    private String ownerLabel;

    @Column(name = "assistant_labels")
    private String assistantLabels;

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
        update(name, null, url, imageUrl, price, status, dueDate, recurrenceRule);
    }

    public void update(String name, String description, String url, String imageUrl, BigDecimal price, ItemStatus status, Instant dueDate, String recurrenceRule) {
        update(name, description, url, imageUrl, price, status, dueDate, recurrenceRule, null, null);
    }

    public void update(String name, String description, String url, String imageUrl, BigDecimal price, ItemStatus status, Instant dueDate, String recurrenceRule, String quantity, String category) {
        update(name, description, url, imageUrl, price, status, dueDate, recurrenceRule, quantity, category, null, null);
    }

    public void update(String name, String description, String url, String imageUrl, BigDecimal price, ItemStatus status, Instant dueDate, String recurrenceRule, String quantity, String category, String ownerLabel, String assistantLabels) {
        this.name = name;
        this.description = description;
        this.url = url;
        this.imageUrl = imageUrl;
        this.price = price;
        this.status = status == null ? ItemStatus.OPEN : status;
        this.dueDate = dueDate;
        this.recurrenceRule = recurrenceRule;
        this.quantity = quantity;
        this.category = category;
        this.ownerLabel = ownerLabel;
        this.assistantLabels = assistantLabels;
    }

    public String getId() { return id; }
    public ListEntity getList() { return list; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public ItemStatus getStatus() { return status; }
    public void setStatus(ItemStatus status) { this.status = status; }
    public Instant getDueDate() { return dueDate; }
    public void setDueDate(Instant dueDate) { this.dueDate = dueDate; }
    public String getRecurrenceRule() { return recurrenceRule; }
    public void setRecurrenceRule(String recurrenceRule) { this.recurrenceRule = recurrenceRule; }
    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getReservedByGuest() { return reservedByGuest; }
    public Instant getLastCompletedAt() { return lastCompletedAt; }
    public void setLastCompletedAt(Instant lastCompletedAt) { this.lastCompletedAt = lastCompletedAt; }
    public String getOwnerLabel() { return ownerLabel; }
    public String getAssistantLabels() { return assistantLabels; }
}
