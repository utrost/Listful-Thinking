package app.listful.lists;

import app.listful.domain.User;
import app.listful.lists.dto.ListRequest;
import app.listful.lists.dto.ListResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lists")
public class ListController {
    private final ListService listService;

    public ListController(ListService listService) {
        this.listService = listService;
    }

    @GetMapping
    public List<ListResponse> list(Authentication authentication) {
        return listService.findOwnedLists(currentUser(authentication));
    }

    @PostMapping
    public ResponseEntity<ListResponse> create(@Valid @RequestBody ListRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(listService.create(currentUser(authentication), request));
    }

    @GetMapping("/{id}")
    public ListResponse get(@PathVariable String id, Authentication authentication) {
        return listService.getOwned(currentUser(authentication), id);
    }

    @PutMapping("/{id}")
    public ListResponse update(@PathVariable String id, @Valid @RequestBody ListRequest request, Authentication authentication) {
        return listService.update(currentUser(authentication), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication authentication) {
        listService.delete(currentUser(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return CurrentUser.from(authentication);
    }
}
