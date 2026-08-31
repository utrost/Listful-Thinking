package app.listful.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.enums.UserRole;
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
    "spring.datasource.url=jdbc:sqlite:file:auth-controller-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=false"
})
class AuthControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void firstRegistrationCreatesAdminAndAuthenticatesSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("""
                    {"username":"uwe","email":"uwe@example.test","password":"correct horse battery staple"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("uwe"))
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andReturn();

        assertThat(result.getRequest().getSession(false)).isNotNull();

        assertThat(userRepository.findByUsername("uwe"))
            .get()
            .satisfies(user -> {
                assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
                assertThat(user.getPasswordHash()).startsWith("$2");
            });

        mockMvc.perform(get("/api/v1/auth/me")
                .session((MockHttpSession) result.getRequest().getSession(false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("uwe"))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void secondRegistrationIsBlockedWhenRegistrationDisabled() throws Exception {
        register("admin", "admin@example.test", "password one");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("""
                    {"username":"martha","email":"martha@example.test","password":"password two"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("registration_disabled"));
    }

    @Test
    void loginMeAndLogoutWorkWithSessionCookies() throws Exception {
        register("uwe", "uwe@example.test", "correct horse battery staple");

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("""
                    {"username":"uwe","password":"correct horse battery staple"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("uwe"))
            .andReturn();

        assertThat(login.getRequest().getSession(false)).isNotNull();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("uwe"));

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isUnauthorized());
    }

    private void register(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                    .formatted(username, email, password)))
            .andExpect(status().isCreated());
    }
}
