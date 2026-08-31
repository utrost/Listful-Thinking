package app.listful.lists;

import app.listful.api.ResourceNotFoundException;
import app.listful.auth.ListfulUserPrincipal;
import app.listful.domain.User;
import org.springframework.security.core.Authentication;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static User from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ListfulUserPrincipal principal)) {
            throw new ResourceNotFoundException("User not found");
        }
        return principal.user();
    }
}
