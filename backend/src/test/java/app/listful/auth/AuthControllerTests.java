package app.listful.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.enums.UserRole;
import app.listful.domain.repository.SettingRepository;
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
    "listful.registration-enabled=false", "spring.mail.host=mail.test"
})
class AuthControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired private AuthService authService;
    @Autowired private app.listful.domain.repository.AuthTokenRepository authTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SettingRepository settingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JavaMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
        settingRepository.deleteAll();
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
    void publicAuthSettingsExposeOnlyRegistrationAndEmailAvailability() throws Exception {
        mockMvc.perform(get("/api/v1/auth/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.registrationAvailable").value(true));

        MockHttpSession admin = registerAndReturnSession("admin", "admin@example.test", "password one");

        mockMvc.perform(get("/api/v1/auth/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationAvailable").value(false));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/settings")
                .session(admin)
                .contentType("application/json")
                .content("{\"registrationEnabled\":true}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationAvailable").value(true));
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


    @Test
    void loginAndRegistrationRotateExistingSessions() throws Exception {
        MockHttpSession anonymous = new MockHttpSession();
        String original = anonymous.getId();
        mockMvc.perform(post("/api/v1/auth/register").session(anonymous)
            .contentType("application/json")
            .content("{\"username\":\"owner\",\"password\":\"strong-password\"}"))
            .andExpect(status().isCreated());
        assertThat(anonymous.getId()).isNotEqualTo(original);
        MockHttpSession loginSession = new MockHttpSession();
        String beforeLogin = loginSession.getId();
        mockMvc.perform(post("/api/v1/auth/login").session(loginSession)
            .contentType("application/json")
            .content("{\"username\":\"owner\",\"password\":\"strong-password\"}"))
            .andExpect(status().isOk());
        assertThat(loginSession.getId()).isNotEqualTo(beforeLogin);
    }

    @Test
    void resetRevokesOldSessionsAndOutstandingMagicTokens() throws Exception {
        MockHttpSession oldSession = registerAndReturnSession("owner", "owner@example.test", "strong-password");
        mockMvc.perform(post("/api/v1/auth/magic-link").contentType("application/json")
            .content("{\"email\":\"owner@example.test\"}")).andExpect(status().isNoContent());
        String magic = extractToken(sentMail().getText());
        org.mockito.Mockito.reset(mailSender);
        mockMvc.perform(post("/api/v1/auth/password-reset").contentType("application/json")
            .content("{\"email\":\"owner@example.test\"}")).andExpect(status().isNoContent());
        String reset = extractToken(sentMail().getText());
        mockMvc.perform(post("/api/v1/auth/password-reset/consume").contentType("application/json")
            .content("{\"token\":\"%s\",\"password\":\"new-password\"}".formatted(reset)))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").session(oldSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/magic-link/consume").contentType("application/json")
            .content("{\"token\":\"%s\"}".formatted(magic))).andExpect(status().isUnauthorized());
    }

    @Test
    void sharedEmailRequiresUsernameWithoutRevealingAmbiguity() throws Exception {
        MockHttpSession admin = registerAndReturnSession("owner", "family@example.test", "strong-password");
        mockMvc.perform(post("/api/v1/admin/users").session(admin).contentType("application/json")
            .content("{\"username\":\"member\",\"email\":\"family@example.test\",\"password\":\"strong-password\",\"role\":\"USER\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/password-reset").contentType("application/json")
            .content("{\"email\":\"family@example.test\"}")).andExpect(status().isNoContent());
        org.mockito.Mockito.verifyNoInteractions(mailSender);
        mockMvc.perform(post("/api/v1/auth/password-reset").contentType("application/json")
            .content("{\"email\":\"family@example.test\",\"username\":\"member\"}")).andExpect(status().isNoContent());
        assertThat(sentMail().getTo()).containsExactly("family@example.test");
    }

    @Test
    void missingMailIsReportedForBothRecoveryEndpoints() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "mailHost", "");
        try {
            mockMvc.perform(get("/api/v1/auth/settings"))
                .andExpect(jsonPath("$.emailRecoveryAvailable").value(false));
            for (String route : java.util.List.of("magic-link", "password-reset")) {
                mockMvc.perform(post("/api/v1/auth/" + route).contentType("application/json")
                    .content("{\"email\":\"unknown@example.test\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("email_unavailable"));
            }
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(authService, "mailHost", "mail.test");
        }
    }

    @Test
    void failedDeliveryDoesNotClaimSuccessOrLeaveUsableTokens() throws Exception {
        register("uwe", "uwe@example.test", "correct horse battery staple");
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("offline"))
            .when(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        for (String route : java.util.List.of("magic-link", "password-reset")) {
            mockMvc.perform(post("/api/v1/auth/" + route).contentType("application/json")
                .content("{\"email\":\"  uwe@example.test  \"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("email_delivery_failed"));
            assertThat(authTokenRepository.count()).isZero();
        }
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

    private MockHttpSession registerAndReturnSession(String username, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                    .formatted(username, email, password)))
            .andExpect(status().isCreated())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void register(String username, String email, String password) throws Exception {
        registerAndReturnSession(username, email, password);
    }
}
