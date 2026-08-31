package app.listful.sharing;

import app.listful.domain.User;
import app.listful.lists.CurrentUser;
import app.listful.sharing.dto.ListShareResponse;
import app.listful.sharing.dto.ShareListRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lists/{listId}/shares")
public class ListSharingController {
    private final ListSharingService listSharingService;

    public ListSharingController(ListSharingService listSharingService) {
        this.listSharingService = listSharingService;
    }

    @GetMapping
    public List<ListShareResponse> listShares(@PathVariable String listId, Authentication authentication) {
        return listSharingService.listShares(currentUser(authentication), listId);
    }

    @PostMapping
    public ResponseEntity<ListShareResponse> share(@PathVariable String listId, @Valid @RequestBody ShareListRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(listSharingService.shareWithUser(currentUser(authentication), listId, request));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Void> revoke(@PathVariable String listId, @PathVariable String username, Authentication authentication) {
        listSharingService.revoke(currentUser(authentication), listId, username);
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        return CurrentUser.from(authentication);
    }
}
