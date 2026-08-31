package app.listful.notifications;

import app.listful.domain.User;
import app.listful.lists.CurrentUser;
import app.listful.notifications.dto.NotificationResponse;
import java.util.List;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> listUnread(Authentication authentication) {
        return notificationService.findUnread(currentUser(authentication), LocaleContextHolder.getLocale());
    }

    @PutMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable String notificationId, Authentication authentication) {
        return notificationService.markRead(currentUser(authentication), notificationId, LocaleContextHolder.getLocale());
    }

    private User currentUser(Authentication authentication) {
        return CurrentUser.from(authentication);
    }
}
