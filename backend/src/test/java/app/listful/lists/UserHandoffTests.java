package app.listful.lists;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.repository.AuthTokenRepository;
import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.ListShareRepository;
import app.listful.domain.repository.NotificationRepository;
import app.listful.domain.repository.SecurityEventRepository;
import app.listful.domain.repository.SettingRepository;
import app.listful.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:user-handoff-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=true"
})
class UserHandoffTests {
    @Autowired MockMvc mvc;
    @Autowired AuthTokenRepository tokens;
    @Autowired NotificationRepository notifications;
    @Autowired ItemRepository items;
    @Autowired ListShareRepository shares;
    @Autowired ListRepository lists;
    @Autowired UserRepository users;
    @Autowired SecurityEventRepository events;
    @Autowired SettingRepository settings;

    @BeforeEach
    void clean() {
        tokens.deleteAll();
        notifications.deleteAll();
        items.deleteAll();
        shares.deleteAll();
        lists.deleteAll();
        events.deleteAll();
        users.deleteAll();
        settings.deleteAll();
    }

    @Test
    void adminSetupLinkIsOneTimeAndRecoveryRevokesExistingSessions() throws Exception {
        MockHttpSession admin = register("admin");
        MvcResult created = mvc.perform(post("/api/v1/admin/users/with-setup").session(admin)
                .contentType("application/json")
                .content("{\"username\":\"newuser\",\"role\":\"USER\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.username").value("newuser"))
            .andExpect(jsonPath("$.emailed").value(false))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andReturn();
        String setup = tokenFrom(created, "$.setupUrl");
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\"newuser\",\"password\":\"guessed-password\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/password-reset/consume").contentType("application/json")
                .content("{\"token\":\"%s\",\"password\":\"first chosen password\"}".formatted(setup)))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/password-reset/consume").contentType("application/json")
                .content("{\"token\":\"%s\",\"password\":\"another password\"}".formatted(setup)))
            .andExpect(status().isUnauthorized());
        MockHttpSession user = login("newuser", "first chosen password");
        String userId = users.findByUsername("newuser").orElseThrow().getId();

        MvcResult recovery = mvc.perform(post("/api/v1/admin/users/{id}/setup-link", userId).session(admin))
            .andExpect(status().isOk())
            .andReturn();
        String recoveryToken = tokenFrom(recovery, "$.setupUrl");
        mvc.perform(post("/api/v1/auth/password-reset/consume").contentType("application/json")
                .content("{\"token\":\"%s\",\"password\":\"second chosen password\"}".formatted(recoveryToken)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").session(user)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\"newuser\",\"password\":\"first chosen password\"}"))
            .andExpect(status().isUnauthorized());
        MockHttpSession recovered = login("newuser", "second chosen password");
        mvc.perform(post("/api/v1/auth/change-password").session(recovered).contentType("application/json")
                .content("{\"currentPassword\":\"second chosen password\",\"newPassword\":\"third chosen password\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").session(recovered)).andExpect(status().isUnauthorized());
        login("newuser", "third chosen password");
        assertThat(events.findByType("admin_setup_link_issued")).hasSize(1);
    }

    @Test
    void sharingNotifiesRecipientAndOwnerCanTransferWithoutGrantingAdminContentAccess() throws Exception {
        MockHttpSession admin = register("admin");
        MockHttpSession owner = register("owner");
        MockHttpSession receiver = register("receiver");
        String id = createList(owner, "Family plan");
        mvc.perform(post("/api/v1/lists/{id}/shares", id).session(owner).contentType("application/json")
                .content("{\"username\":\"receiver\",\"permission\":\"READ\"}"))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/notifications").session(receiver))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].message").value("Family plan was shared with you by owner."));
        mvc.perform(get("/api/v1/lists/{id}", id).session(admin)).andExpect(status().isNotFound());
        MvcResult publicShare = mvc.perform(post("/api/v1/lists/{id}/public-share", id).session(owner))
            .andExpect(status().isCreated()).andReturn();
        String publicToken = JsonPath.read(publicShare.getResponse().getContentAsString(), "$.shareToken");
        mvc.perform(post("/api/v1/lists/{id}/transfer", id).session(receiver).contentType("application/json")
                .content("{\"username\":\"admin\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/lists/{id}/transfer", id).session(owner).contentType("application/json")
                .content("{\"username\":\"receiver\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/lists/{id}", id).session(owner)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/share/{token}", publicToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/lists/{id}", id).session(receiver))
            .andExpect(status().isOk()).andExpect(jsonPath("$.access").value("OWNER"));
        assertThat(shares.existsByListIdAndUserId(id, users.findByUsername("receiver").orElseThrow().getId())).isFalse();
        assertThat(events.findByType("list_ownership_transferred")).hasSize(1);
    }

    @Test
    void adminMayTransferOnlyInactiveOwnersListsAndCannotTransferToSelf() throws Exception {
        MockHttpSession admin = register("admin");
        MockHttpSession owner = register("owner");
        MockHttpSession receiver = register("receiver");
        String id = createList(owner, "Private task plan");
        String archivedId = createList(owner, "Archived work");
        mvc.perform(post("/api/v1/lists/{id}/archive", archivedId).session(owner).contentType("application/json")
                .content("{\"archived\":true}"))
            .andExpect(status().isOk());
        String ownerId = users.findByUsername("owner").orElseThrow().getId();
        mvc.perform(post("/api/v1/admin/users/{id}/transfer-lists", ownerId).session(admin).contentType("application/json")
                .content("{\"username\":\"receiver\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("owner_active"));
        mvc.perform(patch("/api/v1/admin/users/{id}", ownerId).session(admin).contentType("application/json")
                .content("{\"active\":false}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/users/{id}/transfer-lists", ownerId).session(admin).contentType("application/json")
                .content("{\"username\":\"admin\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("self_transfer"));
        mvc.perform(post("/api/v1/admin/users/{id}/transfer-lists", ownerId).session(receiver).contentType("application/json")
                .content("{\"username\":\"receiver\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/users/{id}/transfer-lists", ownerId).session(admin).contentType("application/json")
                .content("{\"username\":\"receiver\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.transferred").value(2));
        mvc.perform(get("/api/v1/lists/{id}", id).session(receiver))
            .andExpect(status().isOk()).andExpect(jsonPath("$.access").value("OWNER"));
        mvc.perform(get("/api/v1/lists/{id}", id).session(admin)).andExpect(status().isNotFound());
        assertThat(lists.findById(archivedId).orElseThrow().getUser().getId())
            .isEqualTo(users.findByUsername("receiver").orElseThrow().getId());
        assertThat(events.findByType("admin_list_ownership_transferred")).hasSize(1);
    }

    private MockHttpSession register(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content("{\"username\":\"%s\",\"password\":\"a long enough password\"}".formatted(username)))
            .andExpect(status().isCreated()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String createList(MockHttpSession session, String title) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/lists").session(session).contentType("application/json")
                .content("{\"title\":\"%s\",\"type\":\"TODO\"}".formatted(title)))
            .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String tokenFrom(MvcResult result, String path) throws Exception {
        String url = JsonPath.read(result.getResponse().getContentAsString(), path);
        return url.substring(url.indexOf("token=") + 6);
    }
}
