package app.listful.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.repository.UserRepository;
import app.listful.auth.dto.RegisterRequest;
import app.listful.domain.enums.UserRole;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:bootstrap-protection-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=false",
    "listful.bootstrap.require-token=true",
    "listful.bootstrap.token=bootstrap-protection-test-secret-32-chars"
})
class BootstrapProtectionTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private AuthService authService;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void firstAdminRequiresServerSetupCodeEvenWhenRegistrationIsOtherwiseOpen() throws Exception {
        mockMvc.perform(get("/api/v1/auth/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationAvailable").value(true))
            .andExpect(jsonPath("$.bootstrapRequired").value(true));

        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json")
            .content("""
                {"username":"attacker","password":"strong-password"}
                """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("bootstrap_token_invalid"));

        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json")
            .content("""
                {"username":"attacker","password":"strong-password","bootstrapToken":"wrong-code"}
                """))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json")
            .content("""
                {"username":"owner","password":"strong-password","bootstrapToken":"bootstrap-protection-test-secret-32-chars"}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(post("/api/v1/auth/register").contentType("application/json")
            .content("""
                {"username":"another","password":"strong-password","bootstrapToken":"bootstrap-protection-test-secret-32-chars"}
                """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("registration_disabled"));
    }

    @Test
    void simultaneousFirstRegistrationsCannotBothBecomeAdmin() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> registerAfter(start, "first"));
            var second = pool.submit(() -> registerAfter(start, "second"));
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findAll().get(0).getRole()).isEqualTo(UserRole.ADMIN);
    }

    private boolean registerAfter(CountDownLatch start, String name) throws InterruptedException {
        start.await();
        try {
            authService.register(new RegisterRequest(name, null, "strong-password", "bootstrap-protection-test-secret-32-chars"));
            return true;
        } catch (RegistrationDisabledException ex) {
            return false;
        }
    }
}
