package app.listful.lists;

import app.listful.api.ValidationFailedException;
import app.listful.domain.Item;
import app.listful.domain.ListEntity;
import app.listful.domain.User;
import app.listful.domain.enums.ListType;
import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.lists.dto.CloneListRequest;
import app.listful.lists.dto.ListRequest;
import app.listful.lists.dto.ListResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListService {
    private final ListRepository listRepository;
    private final ItemRepository itemRepository;
    private final ListAccessService listAccessService;

    public ListService(ListRepository listRepository, ItemRepository itemRepository, ListAccessService listAccessService) {
        this.listRepository = listRepository;
        this.itemRepository = itemRepository;
        this.listAccessService = listAccessService;
    }

    @Transactional(readOnly = true)
    public List<ListResponse> findOwnedLists(User actor) {
        return listRepository.findAccessibleByUserId(actor.getId()).stream()
            .map(list -> toResponse(actor, list))
            .toList();
    }

    @Transactional
    public ListResponse create(User actor, ListRequest request) {
        validateListType(request);
        ListEntity list = new ListEntity(actor, request.title(), request.description(), request.type(), Instant.now());
        list.update(request.title(), request.description(), request.type(), request.targetDate());
        return toResponse(actor, listRepository.save(list));
    }

    @Transactional(readOnly = true)
    public ListResponse getOwned(User actor, String listId) {
        return toResponse(actor, listAccessService.requireReadableList(actor, listId));
    }

    @Transactional
    public ListResponse update(User actor, String listId, ListRequest request) {
        ListEntity list = listAccessService.requireWritableOwnedList(actor, listId);
        if (!list.isTemplate()) validateListType(request);
        else if (request.targetDate() != null) throw new ValidationFailedException("Set dates when creating a list from the template.");
        if (list.getType() != request.type() && itemRepository.existsByListId(list.getId())) {
            throw new app.listful.api.ConflictException("list_type_in_use", "Empty the list before changing its type.");
        }
        list.update(request.title(), request.description(), request.type(), request.targetDate());
        return toResponse(actor, list);
    }

    @Transactional
    public ListResponse cloneList(User actor, String listId, CloneListRequest request) {
        ListEntity source = listAccessService.requireOwnedList(actor, listId);
        String title = hasText(request.title()) ? request.title().trim() : source.getTitle() + " copy";
        ListEntity clone = new ListEntity(actor, title, source.getDescription(), source.getType(), Instant.now());
        clone.update(title, source.getDescription(), source.getType(), source.getTargetDate());
        ListEntity savedClone = listRepository.save(clone);

        for (Item sourceItem : itemRepository.findByListId(source.getId())) {
            Item copied = new Item(savedClone, sourceItem.getName(), Instant.now());
            copied.update(
                sourceItem.getName(),
                sourceItem.getDescription(),
                sourceItem.getUrl(),
                sourceItem.getImageUrl(),
                sourceItem.getPrice(),
                sourceItem.getStatus(),
                sourceItem.getDueDate(),
                sourceItem.getRecurrenceRule(),
                sourceItem.getQuantity(),
                sourceItem.getCategory(),
                sourceItem.getOwnerLabel(),
                sourceItem.getAssistantLabels()
            );
            copied.setLastCompletedAt(sourceItem.getLastCompletedAt());
            copied.setPriceCurrency(sourceItem.getPriceCurrency());
            itemRepository.save(copied);
        }

        return toResponse(actor, savedClone);
    }

    @Transactional
    public void delete(User actor, String listId) {
        ListEntity list = listAccessService.requireOwnedList(actor, listId);
        list.setDeletedAt(Instant.now());
        cancelPendingImports(list);
    }

    @Transactional(readOnly = true)
    public List<ListResponse> library(User actor, String state) {
        if (!java.util.Set.of("archive", "trash", "templates").contains(state)) throw new ValidationFailedException("Unknown library state.");
        return listRepository.findByUserId(actor.getId()).stream()
            .filter(list -> switch (state) {
                case "trash" -> list.isDeleted();
                case "archive" -> !list.isDeleted() && list.isArchived() && !list.isTemplate();
                default -> !list.isDeleted() && list.isTemplate();
            }).sorted(java.util.Comparator.comparing(ListEntity::getCreatedAt).reversed())
            .map(list -> toResponse(actor, list)).toList();
    }

    @Transactional
    public ListResponse archive(User actor, String id, boolean archived) {
        ListEntity list = listAccessService.requireOwnedList(actor, id);
        if (list.isTemplate()) throw new ValidationFailedException("Templates cannot be archived.");
        list.setArchived(archived);
        if (archived) cancelPendingImports(list);
        return toResponse(actor, list);
    }

    @Transactional
    public ListResponse restore(User actor, String id) {
        ListEntity list = listRepository.findById(id).filter(candidate -> candidate.getUser().getId().equals(actor.getId()))
            .orElseThrow(() -> new app.listful.api.ResourceNotFoundException("List not found"));
        list.setDeletedAt(null);
        list.setArchived(false);
        return toResponse(actor, list);
    }

    @Transactional
    public ListResponse saveTemplate(User actor, String id, String title) {
        ListEntity source = listAccessService.requireOwnedList(actor, id);
        ListEntity template = new ListEntity(actor, title.trim(), source.getDescription(), source.getType(), Instant.now());
        template.setTemplate(true);
        ListEntity saved = listRepository.save(template);
        copyFreshItems(source, saved);
        return toResponse(actor, saved);
    }

    @Transactional
    public ListResponse useTemplate(User actor, String id, String title, Instant targetDate) {
        ListEntity source = listAccessService.requireOwnedList(actor, id);
        if (!source.isTemplate()) throw new ValidationFailedException("Choose a template.");
        ListRequest request = new ListRequest(title.trim(), source.getDescription(), source.getType(), targetDate);
        validateListType(request);
        ListEntity created = new ListEntity(actor, request.title(), request.description(), request.type(), Instant.now());
        created.update(request.title(), request.description(), request.type(), targetDate);
        ListEntity saved = listRepository.save(created);
        copyFreshItems(source, saved);
        return toResponse(actor, saved);
    }

    private void copyFreshItems(ListEntity source, ListEntity destination) {
        for (Item original : itemRepository.findByListId(source.getId())) {
            Item copied = new Item(destination, original.getName().equals("Loading metadata…") ? "Imported item" : original.getName(), Instant.now());
            copied.update(copied.getName(), original.getDescription(), original.getUrl(), original.getImageUrl(), original.getPrice(),
                app.listful.domain.enums.ItemStatus.OPEN, null, original.getRecurrenceRule(), original.getQuantity(), original.getCategory(), original.getOwnerLabel(), original.getAssistantLabels());
            copied.setPriceCurrency(original.getPriceCurrency());
            copied.setImportStatus(java.util.Set.of("FAILED", "PENDING").contains(original.getImportStatus()) ? "FAILED" : "NONE");
            itemRepository.save(copied);
        }
    }

    private void cancelPendingImports(ListEntity list) {
        itemRepository.findByListId(list.getId()).stream().filter(item -> "PENDING".equals(item.getImportStatus()))
            .forEach(item -> item.setImportStatus("FAILED"));
    }

    private void validateListType(ListRequest request) {
        if (request.type() == ListType.EVENT && request.targetDate() == null) {
            throw new ValidationFailedException("Event lists require a target date.");
        }
        if (request.type() != ListType.EVENT && request.targetDate() != null) {
            throw new ValidationFailedException("Only event lists can have a target date.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public ListResponse toResponse(ListEntity list) {
        return toResponse(list.getUser(), list);
    }

    public ListResponse toResponse(User actor, ListEntity list) {
        return new ListResponse(
            list.getId(),
            list.getTitle(),
            list.getDescription(),
            list.getType().name(),
            list.isPublicList(),
            list.getShareToken(),
            list.getPublicShareMode().name(),
            list.getTargetDate() == null ? null : list.getTargetDate().toString(),
            listAccessService.accessMode(actor, list),
            list.isArchived(), list.isTemplate(), list.getDeletedAt() == null ? null : list.getDeletedAt().toString(),
            list.getCreatedAt().toString()
        );
    }
}
