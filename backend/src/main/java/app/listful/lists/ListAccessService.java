package app.listful.lists;

import app.listful.api.ResourceNotFoundException;
import app.listful.domain.ListEntity;
import app.listful.domain.ListShare;
import app.listful.domain.User;
import app.listful.domain.enums.ListSharePermission;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.ListShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListAccessService {
    private final ListRepository listRepository;
    private final ListShareRepository listShareRepository;

    public ListAccessService(ListRepository listRepository, ListShareRepository listShareRepository) {
        this.listRepository = listRepository;
        this.listShareRepository = listShareRepository;
    }

    @Transactional(readOnly = true)
    public ListEntity requireOwnedList(User actor, String listId) {
        return listRepository.findById(listId)
            .filter(list -> list.getUser().getId().equals(actor.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("List not found"));
    }

    @Transactional(readOnly = true)
    public ListEntity requireReadableList(User actor, String listId) {
        return listRepository.findById(listId)
            .filter(list -> list.getUser().getId().equals(actor.getId())
                || listShareRepository.existsByListIdAndUserId(list.getId(), actor.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("List not found"));
    }

    @Transactional(readOnly = true)
    public ListEntity requireContributableList(User actor, String listId) {
        ListEntity list = listRepository.findById(listId)
            .orElseThrow(() -> new ResourceNotFoundException("List not found"));
        if (list.getUser().getId().equals(actor.getId())) {
            return list;
        }
        ListShare share = listShareRepository.findByListIdAndUserId(list.getId(), actor.getId())
            .orElseThrow(() -> new ResourceNotFoundException("List not found"));
        if (share.getPermission() != ListSharePermission.CONTRIBUTE) {
            throw new ResourceNotFoundException("List not found");
        }
        return list;
    }

    @Transactional(readOnly = true)
    public boolean canContribute(User actor, ListEntity list) {
        if (list.getUser().getId().equals(actor.getId())) {
            return true;
        }
        return listShareRepository.findByListIdAndUserId(list.getId(), actor.getId())
            .map(share -> share.getPermission() == ListSharePermission.CONTRIBUTE)
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public String accessMode(User actor, ListEntity list) {
        if (list.getUser().getId().equals(actor.getId())) {
            return "OWNER";
        }
        return listShareRepository.findByListIdAndUserId(list.getId(), actor.getId())
            .map(share -> share.getPermission().name())
            .orElse("NONE");
    }
}
