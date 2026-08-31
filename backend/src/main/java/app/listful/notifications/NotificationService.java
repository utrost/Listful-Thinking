package app.listful.notifications;

import app.listful.api.ResourceNotFoundException;
import app.listful.domain.Notification;
import app.listful.domain.User;
import app.listful.domain.repository.NotificationRepository;
import app.listful.notifications.dto.NotificationResponse;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final MessageSource messageSource;

    public NotificationService(NotificationRepository notificationRepository, MessageSource messageSource) {
        this.notificationRepository = notificationRepository;
        this.messageSource = messageSource;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findUnread(User actor, Locale locale) {
        return notificationRepository.findByUserIdAndReadAtIsNull(actor.getId()).stream()
            .map(notification -> toResponse(notification, locale))
            .toList();
    }

    @Transactional
    public NotificationResponse markRead(User actor, String notificationId, Locale locale) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!notification.getUser().getId().equals(actor.getId())) {
            throw new ResourceNotFoundException("Notification not found");
        }
        notification.markRead(Instant.now());
        return toResponse(notification, locale);
    }

    private NotificationResponse toResponse(Notification notification, Locale locale) {
        return new NotificationResponse(
            notification.getId(),
            notification.getMessageKey(),
            messageSource.getMessage(notification.getMessageKey(), messageArguments(notification), notification.getMessageKey(), locale),
            notification.getReadAt() == null ? null : notification.getReadAt().toString(),
            notification.getCreatedAt().toString()
        );
    }

    private Object[] messageArguments(Notification notification) {
        String raw = notification.getMessageArgs();
        if (raw == null || raw.isBlank()) {
            return new Object[0];
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length >= 3) {
            return new Object[] {parts[1], parts[2]};
        }
        return parts;
    }
}
