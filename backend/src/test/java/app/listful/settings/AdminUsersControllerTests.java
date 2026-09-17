package app.listful.settings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.listful.domain.repository.SettingRepository;
import app.listful.domain.repository.SecurityEventRepository;
import app.listful.domain.repository.UserRepository;
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
    "spring.datasource.url=jdbc:sqlite:file:admin-users-controller-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=true"
})
class AdminUsersControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SettingRepository settingRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @BeforeEach
    void cleanDatabase() {
        securityEventRepository.deleteAll();
        userRepository.deleteAll();
        settingRepository.deleteAll();
    }

    @Test
    void adminCanListUsersWithoutPasswordHashes() throws Exception {
        MockHttpSession adminSession = register("admin", "admin@example.test", "correct horse battery staple");
        register("martha", "martha@example.test", "another good password");

        mockMvc.perform(get("/api/v1/admin/users").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].username").value("admin"))
            .andExpect(jsonPath("$[0].role").value("ADMIN"))
            .andExpect(jsonPath("$[0].email").value("admin@example.test"))
            .andExpect(jsonPath("$[0].createdAt").exists())
            .andExpect(jsonPath("$[0].active").value(true))
            .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$[1].username").value("martha"))
            .andExpect(jsonPath("$[1].role").value("USER"));
    }

    @Test
    void nonAdminCannotListUsers() throws Exception {
        register("admin", "admin@example.test", "correct horse battery staple");
        MockHttpSession userSession = register("martha", "martha@example.test", "another good password");

        mockMvc.perform(get("/api/v1/admin/users").session(userSession))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateUserEvenWhenPublicRegistrationIsDisabled() throws Exception {
        MockHttpSession adminSession = register("admin", "admin@example.test", "correct horse battery staple");

        mockMvc.perform(post("/api/v1/admin/users").session(adminSession)
                .contentType("application/json")
                .content("{\"username\":\"bob\",\"email\":\"bob@example.test\",\"password\":\"admin set password\",\"role\":\"USER\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("bob"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(securityEventRepository.findByType("admin_user_created"))
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getActorId()).isEqualTo(userRepository.findByUsername("admin").orElseThrow().getId());
                assertThat(event.getPath()).isEqualTo("/api/v1/admin/users");
                assertThat(event.getDetails()).contains("targetUsername=bob", "targetRole=USER");
                assertThat(event.getDetails()).doesNotContain("admin set password");
            });
    }

    @Test
    void adminCanDeactivateUserAndDeactivatedUserCannotLogin() throws Exception {
        MockHttpSession adminSession = register("admin", "admin@example.test", "correct horse battery staple");
        MockHttpSession userSession = register("martha", "martha@example.test", "another good password");
        MockHttpSession secondUserSession = login("martha", "another good password");
        String userId = userRepository.findByUsername("martha").orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/admin/users/{id}", userId).session(adminSession)
                .contentType("application/json")
                .content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("martha"))
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"martha\",\"password\":\"another good password\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/me").session(userSession))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/lists").session(secondUserSession))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/admin/users/{id}", userId).session(adminSession)
                .contentType("application/json")
                .content("{\"active\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));

        assertThat(securityEventRepository.findByType("admin_user_active_changed"))
            .hasSize(2)
            .allSatisfy(event -> {
                assertThat(event.getActorId()).isEqualTo(userRepository.findByUsername("admin").orElseThrow().getId());
                assertThat(event.getPath()).isEqualTo("/api/v1/admin/users/%s".formatted(userId));
                assertThat(event.getDetails()).contains("targetUsername=martha");
            });

        assertThatThrownBy(() -> userSession.getAttribute("SPRING_SECURITY_CONTEXT"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> secondUserSession.getAttribute("SPRING_SECURITY_CONTEXT"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotDeactivateOnlyActiveAdmin() throws Exception {
        MockHttpSession adminSession = register("admin", "admin@example.test", "correct horse battery staple");
        String adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/admin/users/{id}", adminId).session(adminSession)
                .contentType("application/json")
                .content("{\"active\":false}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("last_active_admin"));

        mockMvc.perform(get("/api/v1/admin/users").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void canDeactivateOneAdminWhenAnotherActiveAdminRemains() throws Exception {
        MockHttpSession adminSession = register("admin", "admin@example.test", "correct horse battery staple");
        mockMvc.perform(post("/api/v1/admin/users").session(adminSession)
                .contentType("application/json")
                .content("{\"username\":\"backup\",\"email\":\"backup@example.test\",\"password\":\"admin set password\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("ADMIN"));
        String backupAdminId = userRepository.findByUsername("backup").orElseThrow().getId();

        mockMvc.perform(patch("/api/v1/admin/users/{id}", backupAdminId).session(adminSession)
                .contentType("application/json")
                .content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("backup"))
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void adminCanSeeAllListsWithOwnerMetadata() throws Exception {
        MockHttpSession adminSession = register("admin", "admin@example.test", "correct horse battery staple");
        MockHttpSession userSession = register("martha", "martha@example.test", "another good password");

        mockMvc.perform(post("/api/v1/lists").session(userSession)
                .contentType("application/json")
                .content("{\"title\":\"Martha todos\",\"type\":\"TODO\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/admin/lists").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Martha todos"))
            .andExpect(jsonPath("$[0].ownerUsername").value("martha"))
            .andExpect(jsonPath("$[0].type").value("TODO"));
    }

    private MockHttpSession register(String username, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                    .formatted(username, email, password)))
            .andExpect(status().isCreated())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
