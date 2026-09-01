package app.listful.lists;

import app.listful.api.ValidationFailedException;
import app.listful.domain.ListEntity;
import app.listful.domain.User;
import app.listful.domain.enums.ListType;
import app.listful.domain.repository.ListRepository;
import app.listful.lists.dto.ListRequest;
import app.listful.lists.dto.ListResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListService {
    private final ListRepository listRepository;
    private final ListAccessService listAccessService;

    public ListService(ListRepository listRepository, ListAccessService listAccessService) {
        this.listRepository = listRepository;
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
        validateListType(request);
        ListEntity list = listAccessService.requireOwnedList(actor, listId);
        list.update(request.title(), request.description(), request.type(), request.targetDate());
        return toResponse(actor, list);
    }

    @Transactional
    public void delete(User actor, String listId) {
        ListEntity list = listAccessService.requireOwnedList(actor, listId);
        listRepository.delete(list);
    }

    private void validateListType(ListRequest request) {
        if (request.type() == ListType.EVENT && request.targetDate() == null) {
            throw new ValidationFailedException("Event lists require a target date.");
        }
        if (request.type() != ListType.EVENT && request.targetDate() != null) {
            throw new ValidationFailedException("Only event lists can have a target date.");
        }
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
            list.getTargetDate() == null ? null : list.getTargetDate().toString(),
            listAccessService.accessMode(actor, list),
            list.getCreatedAt().toString()
        );
    }
}
