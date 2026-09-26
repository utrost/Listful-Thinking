package app.listful.lists;

import app.listful.domain.User;
import app.listful.lists.dto.CloneListRequest;
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

    @GetMapping("/library")
    public List<ListResponse> library(@org.springframework.web.bind.annotation.RequestParam String state, Authentication authentication) {
        return listService.library(currentUser(authentication), state);
    }

    @PostMapping("/{id}/archive")
    public ListResponse archive(@PathVariable String id, @Valid @RequestBody app.listful.lists.dto.ArchiveListRequest request, Authentication authentication) {
        return listService.archive(currentUser(authentication), id, request.archived());
    }

    @PostMapping("/{id}/restore")
    public ListResponse restore(@PathVariable String id, Authentication authentication) {
        return listService.restore(currentUser(authentication), id);
    }

    @PostMapping("/{id}/template")
    public ResponseEntity<ListResponse> saveTemplate(@PathVariable String id, @Valid @RequestBody app.listful.lists.dto.TemplateRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(listService.saveTemplate(currentUser(authentication), id, request.title()));
    }

    @PostMapping("/{id}/instantiate")
    public ResponseEntity<ListResponse> useTemplate(@PathVariable String id, @Valid @RequestBody app.listful.lists.dto.TemplateRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(listService.useTemplate(currentUser(authentication), id, request.title(), request.targetDate()));
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

    @PostMapping("/{id}/clone")
    public ResponseEntity<ListResponse> cloneList(@PathVariable String id, @Valid @RequestBody CloneListRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(listService.cloneList(currentUser(authentication), id, request));
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
