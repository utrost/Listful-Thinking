package app.listful.items;

import app.listful.domain.User;
import app.listful.items.dto.ItemRequest;
import app.listful.items.dto.ItemResponse;
import app.listful.items.dto.PostponeRequest;
import app.listful.lists.CurrentUser;
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
@RequestMapping("/api/v1")
public class ItemController {
    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/lists/{listId}/items")
    public List<ItemResponse> listItems(@PathVariable String listId, Authentication authentication) {
        return itemService.findItems(currentUser(authentication), listId);
    }

    @PostMapping("/lists/{listId}/items")
    public ResponseEntity<ItemResponse> create(@PathVariable String listId, @Valid @RequestBody ItemRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.create(currentUser(authentication), listId, request));
    }

    @PutMapping("/items/{itemId}")
    public ItemResponse update(@PathVariable String itemId, @Valid @RequestBody ItemRequest request, Authentication authentication) {
        return itemService.update(currentUser(authentication), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable String itemId, Authentication authentication) {
        itemService.delete(currentUser(authentication), itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/lists/{listId}/items/completed")
    public ResponseEntity<Void> clearCompleted(@PathVariable String listId, Authentication authentication) {
        itemService.clearCompleted(currentUser(authentication), listId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/items/{itemId}/skip")
    public ItemResponse skip(@PathVariable String itemId, Authentication authentication) {
        return itemService.skipRecurringChore(currentUser(authentication), itemId);
    }

    @PostMapping("/items/{itemId}/postpone")
    public ItemResponse postpone(@PathVariable String itemId, @Valid @RequestBody PostponeRequest request, Authentication authentication) {
        return itemService.postponeChore(currentUser(authentication), itemId, request.days());
    }

    private User currentUser(Authentication authentication) {
        return CurrentUser.from(authentication);
    }
}
