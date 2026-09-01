package app.listful.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import app.listful.domain.Item;
import app.listful.domain.ListEntity;
import app.listful.domain.Notification;
import app.listful.domain.User;
import app.listful.domain.enums.ItemStatus;
import app.listful.domain.enums.ListType;
import app.listful.domain.enums.UserRole;
import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.NotificationRepository;
import app.listful.domain.repository.SettingRepository;
import app.listful.domain.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:reminder-service-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "spring.mail.host=",
    "spring.mail.username=",
    "spring.mail.password="
})
class ReminderServiceTests {
    private static final Instant NOW = Instant.parse("2027-01-01T08:00:00Z");

    @Autowired UserRepository userRepository;
    @Autowired ListRepository listRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired SettingRepository settingRepository;
    @Autowired ReminderService reminderService;
    @Autowired ConfigurableEnvironment environment;
    @MockBean JavaMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
        TestPropertyValues.of("spring.mail.host=").applyTo(environment);
        reset(mailSender);
        notificationRepository.deleteAll();
        itemRepository.deleteAll();
        listRepository.deleteAll();
        userRepository.deleteAll();
        settingRepository.deleteAll();
    }

    @Test
    void smtpAbsentCreatesInAppNotificationForUpcomingDueItem() {
        User owner = userRepository.save(new User("owner", "owner@example.test", "hash", UserRole.ADMIN, NOW.minusSeconds(3600)));
        ListEntity chores = listRepository.save(new ListEntity(owner, "Chores", null, ListType.CHORE, NOW.minusSeconds(3000)));
        Item item = new Item(chores, "Water plants", NOW.minusSeconds(2000));
        item.update("Water plants", null, null, null, null, NOW.plusSeconds(3600), "FREQ=WEEKLY");
        itemRepository.save(item);

        reminderService.processDueReminders(NOW);

        List<Notification> notifications = notificationRepository.findByUserIdAndReadAtIsNull(owner.getId());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getMessageKey()).isEqualTo("notification.item_due_soon");
        assertThat(notifications.get(0).getMessageArgs()).contains(item.getId(), "Water plants", "2027-01-01");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void duplicateDailyScanDoesNotCreateSecondNotificationForSameItemDay() {
        User owner = userRepository.save(new User("owner", "owner@example.test", "hash", UserRole.ADMIN, NOW.minusSeconds(3600)));
        ListEntity event = listRepository.save(new ListEntity(owner, "Trip", null, ListType.EVENT, NOW.minusSeconds(3000)));
        Item item = new Item(event, "Pack bag", NOW.minusSeconds(2000));
        item.update("Pack bag", null, null, null, null, NOW.plusSeconds(7200), null);
        itemRepository.save(item);

        reminderService.processDueReminders(NOW);
        reminderService.processDueReminders(NOW.plusSeconds(60));

        assertThat(notificationRepository.findByUserIdAndReadAtIsNull(owner.getId())).hasSize(1);
    }

    @Test
    void todoListDueDateCreatesInAppNotification() {
        User owner = userRepository.save(new User("owner", "owner@example.test", "hash", UserRole.ADMIN, NOW.minusSeconds(3600)));
        ListEntity todos = listRepository.save(new ListEntity(owner, "Todo", null, ListType.TODO, NOW.minusSeconds(3000)));
        Item item = new Item(todos, "Call optician", NOW.minusSeconds(2000));
        item.update("Call optician", null, null, null, null, NOW.plusSeconds(5400), null);
        itemRepository.save(item);

        reminderService.processDueReminders(NOW);

        List<Notification> notifications = notificationRepository.findByUserIdAndReadAtIsNull(owner.getId());
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getMessageArgs()).contains(item.getId(), "Call optician", "2027-01-01");
    }

    @Test
    void doneDueItemsDoNotCreateReminderNotifications() {
        User owner = userRepository.save(new User("owner", "owner@example.test", "hash", UserRole.ADMIN, NOW.minusSeconds(3600)));
        ListEntity todos = listRepository.save(new ListEntity(owner, "Todo", null, ListType.TODO, NOW.minusSeconds(3000)));
        Item item = new Item(todos, "Call optician", NOW.minusSeconds(2000));
        item.update("Call optician", null, null, null, null, ItemStatus.DONE, NOW.plusSeconds(5400), null);
        itemRepository.save(item);

        reminderService.processDueReminders(NOW);

        assertThat(notificationRepository.findByUserIdAndReadAtIsNull(owner.getId())).isEmpty();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void smtpConfiguredSendsEmailInsteadOfCreatingInAppNotification() {
        TestPropertyValues.of("spring.mail.host=smtp.example.test").applyTo(environment);
        User owner = userRepository.save(new User("owner", "owner@example.test", "hash", UserRole.ADMIN, NOW.minusSeconds(3600)));
        ListEntity chores = listRepository.save(new ListEntity(owner, "Chores", null, ListType.CHORE, NOW.minusSeconds(3000)));
        Item item = new Item(chores, "Water plants", NOW.minusSeconds(2000));
        item.update("Water plants", null, null, null, null, NOW.plusSeconds(3600), "FREQ=WEEKLY");
        itemRepository.save(item);

        reminderService.processDueReminders(NOW);

        assertThat(notificationRepository.findByUserIdAndReadAtIsNull(owner.getId())).isEmpty();
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

}
