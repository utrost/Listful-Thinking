package app.listful.notifications;

import app.listful.domain.Item;
import app.listful.domain.Notification;
import app.listful.domain.User;
import app.listful.domain.enums.ItemStatus;
import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.NotificationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReminderService {
    static final String ITEM_DUE_SOON_KEY = "notification.item_due_soon";
    private static final Logger logger = LoggerFactory.getLogger(ReminderService.class);

    private final ItemRepository itemRepository;
    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final Environment environment;

    public ReminderService(
        ItemRepository itemRepository,
        NotificationRepository notificationRepository,
        JavaMailSender mailSender,
        Environment environment
    ) {
        this.itemRepository = itemRepository;
        this.notificationRepository = notificationRepository;
        this.mailSender = mailSender;
        this.environment = environment;
    }

    @Scheduled(fixedDelayString = "${listful.reminders.interval-ms:60000}", initialDelay = 60000)
    public void processDailyDueReminders() {
        processDueReminders(Instant.now());
    }

    public void processDueReminders(Instant now) {
        Instant horizon = now.plusSeconds(24 * 60 * 60);
        List<Item> dueItems = itemRepository.findDueItemsBetween(Instant.EPOCH, horizon, List.of(ItemStatus.PURCHASED, ItemStatus.DONE));
        for (Item item : dueItems) {
            if (item.getList().getType() == app.listful.domain.enums.ListType.WISH
                    || item.getList().getType() == app.listful.domain.enums.ListType.GROCERY) continue;
            createOrSendReminder(item, now);
        }
    }

    private void createOrSendReminder(Item item, Instant now) {
        User owner = item.getList().getUser();
        String args = messageArgs(item);
        if (!owner.isActive()) return;
        String deliveryKey = item.getId() + "|" + item.getDueDate();
        if (notificationRepository.existsByDeliveryKey(deliveryKey)
                || notificationRepository.existsByUserIdAndMessageKeyAndMessageArgsAndDeliveryKeyIsNull(owner.getId(), ITEM_DUE_SOON_KEY, args)) {
            return;
        }

        // Persist the inbox fallback before attempting external delivery. A crash must not lose the reminder.
        Notification notification = new Notification(owner, ITEM_DUE_SOON_KEY, args, now);
        notification.setDeliveryKey(deliveryKey);
        try {
            notificationRepository.saveAndFlush(notification);
        } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
            if (notificationRepository.existsByDeliveryKey(deliveryKey)) return;
            throw duplicate;
        }
        if (smtpConfigured() && hasText(owner.getEmail()) && sendMail(owner, item)) {
            notification.markRead(now);
            notificationRepository.save(notification);
        }
    }

    private boolean sendMail(User owner, Item item) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(owner.getEmail());
            String from = environment.getProperty("listful.mail-from", "");
            if (hasText(from)) message.setFrom(from);
            message.setSubject("Listful Thinking reminder");
            message.setText("Upcoming item: " + item.getName() + " is due on " + item.getDueDate());
            mailSender.send(message);
            return true;
        } catch (MailException ex) {
            logger.info("Email reminder failed for user {}: {}", owner.getId(), ex.getMessage());
            return false;
        }
    }

    private boolean smtpConfigured() {
        return hasText(environment.getProperty("spring.mail.host"));
    }

    private String messageArgs(Item item) {
        LocalDate dueDay = item.getDueDate().atZone(ZoneOffset.UTC).toLocalDate();
        return item.getId() + "|" + item.getName() + "|" + dueDay;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
