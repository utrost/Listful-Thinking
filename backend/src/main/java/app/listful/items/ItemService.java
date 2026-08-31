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

    public ItemService(ItemRepository itemRepository, ListAccessService listAccessService) {
        this.itemRepository = itemRepository;
        this.listAccessService = listAccessService;
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
        Item item = new Item(list, request.name(), Instant.now());
        item.update(request.name(), request.url(), request.imageUrl(), request.price(), request.status(), request.dueDate(), request.recurrenceRule());
        return toResponse(itemRepository.save(item));
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
        if (listType != ListType.WISH && hasShoppingFields(request)) {
            throw new ValidationFailedException("Shopping fields are only allowed on wish lists.");
        }
        if (listType == ListType.EVENT && hasText(request.recurrenceRule())) {
            throw new ValidationFailedException("Recurrence rules are only allowed on chore items.");
        }
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
