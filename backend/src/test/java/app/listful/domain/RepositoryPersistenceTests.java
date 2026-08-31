package app.listful.domain;

import static org.assertj.core.api.Assertions.assertThat;

import app.listful.domain.enums.ItemStatus;
import app.listful.domain.enums.ListType;
import app.listful.domain.enums.UserRole;
import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.ListShareRepository;
import app.listful.domain.repository.NotificationRepository;
import app.listful.domain.repository.SettingRepository;
import app.listful.domain.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:repository-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "spring.flyway.enabled=true"
})
class RepositoryPersistenceTests {
    @Autowired UserRepository userRepository;
    @Autowired ListRepository listRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired SettingRepository settingRepository;
    @Autowired ListShareRepository listShareRepository;
    @Autowired NotificationRepository notificationRepository;

    @Test
    void persistsUserListItemShareSettingAndNotification() {
        Instant now = Instant.parse("2026-08-31T12:00:00Z");

        User owner = userRepository.save(new User("uwe", "uwe@example.test", "hash", UserRole.ADMIN, now));
        User sharedUser = userRepository.save(new User("annette", "annette@example.test", "hash2", UserRole.USER, now));

        ListEntity list = listRepository.save(new ListEntity(owner, "Birthday", "Ideas", ListType.WISH, now));
        list.enablePublicShare("abc123");
        list = listRepository.save(list);

        Item item = itemRepository.save(new Item(list, "Plotter pen", now));
        item.setUrl("https://example.test/pen");
        item.setImageUrl("https://example.test/pen.jpg");
        item.setPrice(new BigDecimal("12.50"));
        item.claimForGuest("Martha");
        item = itemRepository.save(item);

        Setting setting = settingRepository.save(new Setting("registration.enabled", "false"));
        ListShare share = listShareRepository.save(new ListShare(list, sharedUser, now));
        Notification notification = notificationRepository.save(new Notification(owner, "notification.dueSoon", "{\"item\":\"Plotter pen\"}", now));

        assertThat(userRepository.findByUsername("uwe")).contains(owner);
        assertThat(listRepository.findByUserId(owner.getId())).containsExactly(list);
        assertThat(listRepository.findByShareToken("abc123")).contains(list);
        assertThat(itemRepository.findByListId(list.getId())).containsExactly(item);
        assertThat(settingRepository.findById("registration.enabled")).contains(setting);
        assertThat(listShareRepository.findByUserId(sharedUser.getId())).containsExactly(share);
        assertThat(notificationRepository.findByUserIdAndReadAtIsNull(owner.getId())).containsExactly(notification);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.CLAIMED);
        assertThat(item.getReservedByGuest()).isEqualTo("Martha");
    }
}
