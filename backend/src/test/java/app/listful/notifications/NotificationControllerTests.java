package app.listful.notifications;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.Notification;
import app.listful.domain.User;
import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.NotificationRepository;
import app.listful.domain.repository.SettingRepository;
import app.listful.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:notification-controller-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=true"
})
class NotificationControllerTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ListRepository listRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired SettingRepository settingRepository;

    @BeforeEach
    void cleanDatabase() {
        notificationRepository.deleteAll();
        itemRepository.deleteAll();
        listRepository.deleteAll();
        userRepository.deleteAll();
        settingRepository.deleteAll();
    }

    @Test
    void userCanReadOwnUnreadNotificationsLocalizedByAcceptLanguage() throws Exception {
        MockHttpSession ownerSession = register("owner");
        User owner = userRepository.findByUsername("owner").orElseThrow();
        notificationRepository.save(new Notification(owner, "notification.item_due_soon", "item-1|Wasser Pflanzen|2027-01-01", Instant.parse("2027-01-01T08:00:00Z")));

        mockMvc.perform(get("/api/v1/notifications").session(ownerSession).header("Accept-Language", "de"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].messageKey").value("notification.item_due_soon"))
            .andExpect(jsonPath("$[0].message").value("Wasser Pflanzen ist am 2027-01-01 fällig."))
            .andExpect(jsonPath("$[0].readAt").doesNotExist());
    }

    @Test
    void userCanMarkOwnNotificationReadButNotAnotherUsersNotification() throws Exception {
        MockHttpSession ownerSession = register("owner");
        MockHttpSession otherSession = register("other");
        User owner = userRepository.findByUsername("owner").orElseThrow();
        Notification notification = notificationRepository.save(new Notification(owner, "notification.item_due_soon", "item-1|Water plants|2027-01-01", Instant.parse("2027-01-01T08:00:00Z")));

        mockMvc.perform(put("/api/v1/notifications/{id}/read", notification.getId()).session(otherSession))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/notifications/{id}/read", notification.getId()).session(ownerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readAt").exists());

        mockMvc.perform(get("/api/v1/notifications").session(ownerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    private MockHttpSession register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"%s\",\"email\":\"%s@example.test\",\"password\":\"correct horse battery staple\"}"
                    .formatted(username, username)))
            .andExpect(status().isCreated())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
