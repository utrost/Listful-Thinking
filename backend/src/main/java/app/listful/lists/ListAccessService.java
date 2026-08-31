package app.listful.lists;

import app.listful.api.ResourceNotFoundException;
import app.listful.domain.ListEntity;
import app.listful.domain.User;
import app.listful.domain.repository.ListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListAccessService {
    private final ListRepository listRepository;

    public ListAccessService(ListRepository listRepository) {
        this.listRepository = listRepository;
    }

    @Transactional(readOnly = true)
    public ListEntity requireOwnedList(User actor, String listId) {
        return listRepository.findById(listId)
            .filter(list -> list.getUser().getId().equals(actor.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("List not found"));
    }
}
