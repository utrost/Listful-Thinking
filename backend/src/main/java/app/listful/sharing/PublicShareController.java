package app.listful.sharing;

import app.listful.domain.User;
import app.listful.lists.CurrentUser;
import app.listful.sharing.dto.GuestClaimRequest;
import app.listful.sharing.dto.PublicItemResponse;
import app.listful.sharing.dto.PublicListResponse;
import app.listful.sharing.dto.PublicShareRequest;
import app.listful.sharing.dto.PublicShareTokenResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1")
public class PublicShareController {
    private final PublicShareService publicShareService;

    public PublicShareController(PublicShareService publicShareService) {
        this.publicShareService = publicShareService;
    }

    @PostMapping("/lists/{listId}/public-share")
    public ResponseEntity<PublicShareTokenResponse> createToken(
        @PathVariable String listId,
        @RequestBody(required = false) PublicShareRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(publicShareService.createToken(currentUser(authentication), listId, request));
    }

    @DeleteMapping("/lists/{listId}/public-share")
    public ResponseEntity<Void> revokeToken(@PathVariable String listId, Authentication authentication) {
        publicShareService.revokeToken(currentUser(authentication), listId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/share/{token}")
    public PublicListResponse getPublicList(@PathVariable String token) {
        return publicShareService.getPublicList(token);
    }

    @PostMapping("/share/{token}/items/{itemId}/claim")
    public PublicItemResponse claim(@PathVariable String token, @PathVariable String itemId, @Valid @RequestBody GuestClaimRequest request) {
        return publicShareService.claim(token, itemId, request);
    }

    private User currentUser(Authentication authentication) {
        return CurrentUser.from(authentication);
    }
}
