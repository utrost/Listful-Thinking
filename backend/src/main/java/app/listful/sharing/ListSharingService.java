package app.listful.sharing;

import app.listful.api.ResourceNotFoundException;
import app.listful.api.ValidationFailedException;
import app.listful.domain.ListEntity;
import app.listful.domain.ListShare;
import app.listful.domain.ListShareId;
import app.listful.domain.User;
import app.listful.domain.repository.ListShareRepository;
import app.listful.domain.repository.UserRepository;
import app.listful.lists.ListAccessService;
import app.listful.sharing.dto.ListShareResponse;
import app.listful.sharing.dto.ShareListRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListSharingService {
    private final ListAccessService listAccessService;
    private final ListShareRepository listShareRepository;
    private final UserRepository userRepository;

    public ListSharingService(ListAccessService listAccessService, ListShareRepository listShareRepository, UserRepository userRepository) {
        this.listAccessService = listAccessService;
        this.listShareRepository = listShareRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ListShareResponse> listShares(User actor, String listId) {
        ListEntity list = listAccessService.requireOwnedList(actor, listId);
        return listShareRepository.findByListId(list.getId()).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ListShareResponse shareWithUser(User actor, String listId, ShareListRequest request) {
        ListEntity list = listAccessService.requireOwnedList(actor, listId);
        User sharedUser = userRepository.findByUsername(request.username())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (sharedUser.getId().equals(actor.getId())) {
            throw new ValidationFailedException("Owners already have access to their lists.");
        }
        return toResponse(listShareRepository.save(new ListShare(list, sharedUser, Instant.now())));
    }

    @Transactional
    public void revoke(User actor, String listId, String username) {
        ListEntity list = listAccessService.requireOwnedList(actor, listId);
        User sharedUser = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException("Share not found"));
        ListShareId id = new ListShareId(list.getId(), sharedUser.getId());
        if (!listShareRepository.existsById(id)) {
            throw new ResourceNotFoundException("Share not found");
        }
        listShareRepository.deleteById(id);
    }

    private ListShareResponse toResponse(ListShare share) {
        return new ListShareResponse(
            share.getList().getId(),
            share.getUser().getId(),
            share.getUser().getUsername(),
            share.getCreatedAt().toString()
        );
    }
}
