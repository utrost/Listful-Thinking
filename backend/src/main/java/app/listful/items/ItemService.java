package app.listful.items;

import app.listful.api.ResourceNotFoundException;
import app.listful.api.ValidationFailedException;
import app.listful.domain.Item;
import app.listful.domain.ListEntity;
import app.listful.domain.User;
import app.listful.domain.enums.ItemStatus;
import app.listful.domain.enums.ListType;
import app.listful.domain.repository.ItemRepository;
import app.listful.items.dto.ItemRequest;
import app.listful.items.dto.ItemResponse;
import app.listful.lists.ListAccessService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {
    private final ItemRepository itemRepository;
    private final ListAccessService listAccessService;
    private final ItemEnrichmentService itemEnrichmentService;

    public ItemService(ItemRepository itemRepository, ListAccessService listAccessService, ItemEnrichmentService itemEnrichmentService) {
        this.itemRepository = itemRepository;
        this.listAccessService = listAccessService;
        this.itemEnrichmentService = itemEnrichmentService;
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> findItems(User actor, String listId) {
        ListEntity list = listAccessService.requireReadableList(actor, listId);
        return itemRepository.findByListId(list.getId()).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ItemResponse create(User actor, String listId, ItemRequest request) {
        ListEntity list = listAccessService.requireContributableList(actor, listId);
        validateForListType(list, request);
        String itemName = itemNameFor(request);
        Item item = new Item(list, itemName, Instant.now());
        item.update(itemName, request.description(), request.url(), request.imageUrl(), request.price(), request.status(), request.dueDate(), request.recurrenceRule(), request.quantity(), request.category(), trimmedOrNull(request.ownerLabel()), trimmedOrNull(request.assistantLabels()));
        Item saved = itemRepository.save(item);
        if (shouldEnrichWishUrlItem(list, request)) {
            itemEnrichmentService.enrichUrlItem(saved.getId(), request.url().trim());
        }
        return toResponse(saved);
    }

    @Transactional
    public ItemResponse update(User actor, String itemId, ItemRequest request) {
        Item item = requireContributableItem(actor, itemId);
        validateForListType(item.getList(), request);
        item.update(request.name(), request.description(), request.url(), request.imageUrl(), request.price(), request.status(), request.dueDate(), request.recurrenceRule(), request.quantity(), request.category(), trimmedOrNull(request.ownerLabel()), trimmedOrNull(request.assistantLabels()));
        advanceCompletedRecurringChore(item);
        return toResponse(item);
    }

    @Transactional
    public void delete(User actor, String itemId) {
        Item item = requireOwnedItem(actor, itemId);
        itemRepository.delete(item);
    }

    @Transactional
    public void clearCompleted(User actor, String listId) {
        ListEntity list = listAccessService.requireOwnedList(actor, listId);
        if (list.getType() != ListType.GROCERY) {
            throw new ValidationFailedException("Clear completed is only available for grocery lists.");
        }
        itemRepository.deleteByListIdAndStatus(list.getId(), ItemStatus.DONE);
    }

    @Transactional
    public ItemResponse skipRecurringChore(User actor, String itemId) {
        Item item = requireContributableItem(actor, itemId);
        requireRecurringChore(item);
        item.setDueDate(nextDueDate(item.getDueDate(), item.getRecurrenceRule()));
        item.setStatus(ItemStatus.OPEN);
        return toResponse(item);
    }

    @Transactional
    public ItemResponse postponeChore(User actor, String itemId, int days) {
        Item item = requireContributableItem(actor, itemId);
        if (item.getList().getType() != ListType.CHORE || item.getDueDate() == null) {
            throw new ValidationFailedException("Only dated chore items can be postponed.");
        }
        item.setDueDate(item.getDueDate().plus(days, ChronoUnit.DAYS));
        item.setStatus(ItemStatus.OPEN);
        return toResponse(item);
    }

    private Item requireOwnedItem(User actor, String itemId) {
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        if (!item.getList().getUser().getId().equals(actor.getId())) {
            throw new ResourceNotFoundException("Item not found");
        }
        return item;
    }

    private Item requireContributableItem(User actor, String itemId) {
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        if (!listAccessService.canContribute(actor, item.getList())) {
            throw new ResourceNotFoundException("Item not found");
        }
        return item;
    }

    private void validateForListType(ListEntity list, ItemRequest request) {
        ListType listType = list.getType();
        if (!hasText(request.name()) && !isWishUrlOnlyCandidate(list, request)) {
            throw new ValidationFailedException("Item name is required.");
        }
        if (listType != ListType.WISH && hasShoppingFields(request)) {
            throw new ValidationFailedException("Shopping fields are only allowed on wish lists.");
        }
        if (listType != ListType.CHORE && hasText(request.recurrenceRule())) {
            throw new ValidationFailedException("Recurrence rules are only allowed on chore items.");
        }
        if (listType == ListType.CHORE && hasText(request.recurrenceRule()) && !isSupportedRecurrence(request.recurrenceRule())) {
            throw unsupportedRecurrenceRule();
        }
        if (listType != ListType.GROCERY && hasGroceryFields(request)) {
            throw new ValidationFailedException("Quantity and category are only allowed on grocery items.");
        }
        if (listType == ListType.GROCERY && request.dueDate() != null) {
            throw new ValidationFailedException("Due dates are only allowed on to-do, chore, and event items.");
        }
        if (listType == ListType.WISH && request.status() == ItemStatus.DONE) {
            throw new ValidationFailedException("Done status is only allowed on to-do, grocery, chore, and event items.");
        }
        if (listType != ListType.WISH && (request.status() == ItemStatus.CLAIMED || request.status() == ItemStatus.PURCHASED)) {
            throw new ValidationFailedException("Claimed and purchased statuses are only allowed on wish list items.");
        }
    }

    private String itemNameFor(ItemRequest request) {
        return hasText(request.name()) ? request.name().trim() : ItemEnrichmentService.PLACEHOLDER_NAME;
    }

    private boolean shouldEnrichWishUrlItem(ListEntity list, ItemRequest request) {
        return list.getType() == ListType.WISH
            && hasText(request.url())
            && (!hasText(request.name())
                || !hasText(request.description())
                || !hasText(request.imageUrl())
                || request.price() == null);
    }

    private boolean isWishUrlOnlyCandidate(ListEntity list, ItemRequest request) {
        return list.getType() == ListType.WISH
            && !hasText(request.name())
            && hasText(request.url());
    }

    private boolean hasShoppingFields(ItemRequest request) {
        return hasText(request.url()) || hasText(request.imageUrl()) || request.price() != null;
    }

    private boolean hasGroceryFields(ItemRequest request) {
        return hasText(request.quantity()) || hasText(request.category());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimmedOrNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private void advanceCompletedRecurringChore(Item item) {
        if (item.getList().getType() == ListType.CHORE
                && item.getStatus() == ItemStatus.DONE
                && hasText(item.getRecurrenceRule())) {
            requireRecurringChore(item);
            item.setLastCompletedAt(Instant.now());
            item.setDueDate(nextDueDate(item.getDueDate(), item.getRecurrenceRule()));
            item.setStatus(ItemStatus.OPEN);
        }
    }

    private void requireRecurringChore(Item item) {
        if (item.getList().getType() != ListType.CHORE || item.getDueDate() == null || !hasText(item.getRecurrenceRule())) {
            throw new ValidationFailedException("Only dated recurring chore items support this action.");
        }
        if (!isSupportedRecurrence(item.getRecurrenceRule())) {
            throw unsupportedRecurrenceRule();
        }
    }

    private Instant nextDueDate(Instant dueDate, String recurrenceRule) {
        return switch (recurrenceRule.trim().toUpperCase()) {
            case "FREQ=DAILY" -> dueDate.plus(1, ChronoUnit.DAYS);
            case "FREQ=WEEKLY" -> dueDate.plus(7, ChronoUnit.DAYS);
            case "FREQ=BIWEEKLY" -> dueDate.plus(14, ChronoUnit.DAYS);
            case "FREQ=MONTHLY" -> dueDate.atZone(ZoneOffset.UTC).plusMonths(1).toInstant();
            case "FREQ=QUARTERLY" -> dueDate.atZone(ZoneOffset.UTC).plusMonths(3).toInstant();
            case "FREQ=ANNUALLY" -> dueDate.atZone(ZoneOffset.UTC).plusYears(1).toInstant();
            default -> throw unsupportedRecurrenceRule();
        };
    }

    private boolean isSupportedRecurrence(String recurrenceRule) {
        String normalized = recurrenceRule.trim().toUpperCase();
        return normalized.equals("FREQ=DAILY")
            || normalized.equals("FREQ=WEEKLY")
            || normalized.equals("FREQ=BIWEEKLY")
            || normalized.equals("FREQ=MONTHLY")
            || normalized.equals("FREQ=QUARTERLY")
            || normalized.equals("FREQ=ANNUALLY");
    }

    private ValidationFailedException unsupportedRecurrenceRule() {
        return new ValidationFailedException("Unsupported recurrence rule. Use FREQ=DAILY, FREQ=WEEKLY, FREQ=BIWEEKLY, FREQ=MONTHLY, FREQ=QUARTERLY, or FREQ=ANNUALLY.");
    }

    private ItemResponse toResponse(Item item) {
        return new ItemResponse(
            item.getId(),
            item.getList().getId(),
            item.getName(),
            item.getDescription(),
            item.getUrl(),
            item.getImageUrl(),
            item.getPrice(),
            item.getStatus().name(),
            item.getDueDate() == null ? null : item.getDueDate().toString(),
            item.getRecurrenceRule(),
            item.getQuantity(),
            item.getCategory(),
            item.getReservedByGuest(),
            item.getLastCompletedAt() == null ? null : item.getLastCompletedAt().toString(),
            item.getOwnerLabel(),
            item.getAssistantLabels()
        );
    }
}
