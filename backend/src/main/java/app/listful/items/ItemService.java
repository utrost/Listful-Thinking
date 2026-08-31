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
        ListEntity list = listAccessService.requireOwnedList(actor, listId);
        validateForListType(list, request);
        String itemName = itemNameFor(request);
        Item item = new Item(list, itemName, Instant.now());
        item.update(itemName, request.url(), request.imageUrl(), request.price(), request.status(), request.dueDate(), request.recurrenceRule());
        Item saved = itemRepository.save(item);
        if (shouldEnrichUrlOnlyItem(list, request)) {
            itemEnrichmentService.enrichUrlOnlyItem(saved.getId(), request.url().trim());
        }
        return toResponse(saved);
    }

    @Transactional
    public ItemResponse update(User actor, String itemId, ItemRequest request) {
        Item item = requireOwnedItem(actor, itemId);
        validateForListType(item.getList(), request);
        item.update(request.name(), request.url(), request.imageUrl(), request.price(), request.status(), request.dueDate(), request.recurrenceRule());
        return toResponse(item);
    }

    @Transactional
    public void delete(User actor, String itemId) {
        Item item = requireOwnedItem(actor, itemId);
        itemRepository.delete(item);
    }

    private Item requireOwnedItem(User actor, String itemId) {
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        if (!item.getList().getUser().getId().equals(actor.getId())) {
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
        if (listType == ListType.EVENT && hasText(request.recurrenceRule())) {
            throw new ValidationFailedException("Recurrence rules are only allowed on chore items.");
        }
    }

    private String itemNameFor(ItemRequest request) {
        return hasText(request.name()) ? request.name().trim() : ItemEnrichmentService.PLACEHOLDER_NAME;
    }

    private boolean shouldEnrichUrlOnlyItem(ListEntity list, ItemRequest request) {
        return isWishUrlOnlyCandidate(list, request);
    }

    private boolean isWishUrlOnlyCandidate(ListEntity list, ItemRequest request) {
        return list.getType() == ListType.WISH
            && !hasText(request.name())
            && hasText(request.url());
    }

    private boolean hasShoppingFields(ItemRequest request) {
        return hasText(request.url()) || hasText(request.imageUrl()) || request.price() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ItemResponse toResponse(Item item) {
        return new ItemResponse(
            item.getId(),
            item.getList().getId(),
            item.getName(),
            item.getUrl(),
            item.getImageUrl(),
            item.getPrice(),
            item.getStatus().name(),
            item.getDueDate() == null ? null : item.getDueDate().toString(),
            item.getRecurrenceRule(),
            item.getReservedByGuest()
        );
    }
}
