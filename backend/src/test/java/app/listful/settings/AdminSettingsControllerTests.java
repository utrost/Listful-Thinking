package app.listful.settings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.repository.SettingRepository;
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
    "spring.datasource.url=jdbc:sqlite:file:admin-settings-controller-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=false"
})
class AdminSettingsControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SettingRepository settingRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
        settingRepository.deleteAll();
    }

    @Test
    void adminCanReadAndEnableRegistrationSetting() throws Exception {
        MockHttpSession adminSession = register("admin", "admin@example.test", "correct horse battery staple");

        mockMvc.perform(get("/api/v1/admin/settings").session(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationEnabled").value(false));

        mockMvc.perform(put("/api/v1/admin/settings").session(adminSession)
                .contentType("application/json")
                .content("""
                    {"registrationEnabled":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationEnabled").value(true));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("""
                    {"username":"martha","email":"martha@example.test","password":"another good password"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void germanAcceptLanguageReturnsGermanRegistrationError() throws Exception {
        register("admin", "admin@example.test", "correct horse battery staple");

        mockMvc.perform(post("/api/v1/auth/register")
                .header("Accept-Language", "de")
                .contentType("application/json")
                .content("""
                    {"username":"martha","email":"martha@example.test","password":"another good password"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("registration_disabled"))
            .andExpect(jsonPath("$.message").value("Registrierung ist deaktiviert."));
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
}
