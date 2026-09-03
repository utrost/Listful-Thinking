package app.listful.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.enums.UserRole;
import app.listful.domain.repository.UserRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.ArgumentCaptor;
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

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JavaMailSender mailSender;

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
    void registeredPasswordsAreStoredAsSaltedBCryptHashes() throws Exception {
        MvcResult admin = mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"uwe\",\"email\":\"uwe@example.test\",\"password\":\"same correct horse battery staple\"}"))
            .andExpect(status().isCreated())
            .andReturn();

        mockMvc.perform(post("/api/v1/admin/users")
                .session((MockHttpSession) admin.getRequest().getSession(false))
                .contentType("application/json")
                .content("{\"username\":\"annette\",\"email\":\"annette@example.test\",\"password\":\"same correct horse battery staple\",\"role\":\"USER\"}"))
            .andExpect(status().isCreated());

        String uweHash = userRepository.findByUsername("uwe").orElseThrow().getPasswordHash();
        String annetteHash = userRepository.findByUsername("annette").orElseThrow().getPasswordHash();

        assertThat(uweHash).startsWith("$2");
        assertThat(annetteHash).startsWith("$2");
        assertThat(uweHash).isNotEqualTo("same correct horse battery staple");
        assertThat(annetteHash).isNotEqualTo("same correct horse battery staple");
        assertThat(uweHash).isNotEqualTo(annetteHash);
        assertThat(passwordEncoder.matches("same correct horse battery staple", uweHash)).isTrue();
        assertThat(passwordEncoder.matches("same correct horse battery staple", annetteHash)).isTrue();
    }

    @Test
    void passwordResetStoresANewSaltedBCryptHash() throws Exception {
        register("uwe", "uwe@example.test", "correct horse battery staple");
        String oldHash = userRepository.findByUsername("uwe").orElseThrow().getPasswordHash();

        mockMvc.perform(post("/api/v1/auth/password-reset")
                .contentType("application/json")
                .content("{\"email\":\"uwe@example.test\"}"))
            .andExpect(status().isNoContent());

        String token = extractToken(sentMail().getText());
        mockMvc.perform(post("/api/v1/auth/password-reset/consume")
                .contentType("application/json")
                .content("{\"token\":\"%s\",\"password\":\"new correct horse battery staple\"}".formatted(token)))
            .andExpect(status().isNoContent());

        String newHash = userRepository.findByUsername("uwe").orElseThrow().getPasswordHash();
        assertThat(newHash).startsWith("$2");
        assertThat(newHash).isNotEqualTo("new correct horse battery staple");
        assertThat(newHash).isNotEqualTo(oldHash);
        assertThat(passwordEncoder.matches("new correct horse battery staple", newHash)).isTrue();
        assertThat(passwordEncoder.matches("correct horse battery staple", newHash)).isFalse();
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

    @Test
    void magicLinkEmailAuthenticatesSessionWithoutPassword() throws Exception {
        register("uwe", "uwe@example.test", "correct horse battery staple");

        mockMvc.perform(post("/api/v1/auth/magic-link")
                .contentType("application/json")
                .content("{\"email\":\"uwe@example.test\"}"))
            .andExpect(status().isNoContent());

        SimpleMailMessage message = sentMail();
        assertThat(message.getTo()).containsExactly("uwe@example.test");
        assertThat(message.getSubject()).contains("magic link");
        String token = extractToken(message.getText());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/magic-link/consume")
                .contentType("application/json")
                .content("{\"token\":\"%s\"}".formatted(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("uwe"))
            .andReturn();

        mockMvc.perform(get("/api/v1/auth/me").session((MockHttpSession) login.getRequest().getSession(false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("uwe"));
    }

    @Test
    void passwordResetEmailLetsUserSetNewPassword() throws Exception {
        register("uwe", "uwe@example.test", "correct horse battery staple");

        mockMvc.perform(post("/api/v1/auth/password-reset")
                .contentType("application/json")
                .content("{\"email\":\"uwe@example.test\"}"))
            .andExpect(status().isNoContent());

        SimpleMailMessage message = sentMail();
        assertThat(message.getTo()).containsExactly("uwe@example.test");
        assertThat(message.getSubject()).contains("password reset");
        String token = extractToken(message.getText());

        mockMvc.perform(post("/api/v1/auth/password-reset/consume")
                .contentType("application/json")
                .content("{\"token\":\"%s\",\"password\":\"new correct horse battery staple\"}".formatted(token)))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"uwe\",\"password\":\"new correct horse battery staple\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("uwe"));
    }

    private SimpleMailMessage sentMail() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private String extractToken(String body) {
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private void register(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                    .formatted(username, email, password)))
            .andExpect(status().isCreated());
    }
}
