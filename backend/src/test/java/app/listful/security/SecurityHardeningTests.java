package app.listful.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.config.SecurityHardeningFilter;
import app.listful.config.SecurityHardeningProperties;
import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.SettingRepository;
import app.listful.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.ContentCachingRequestWrapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:security-hardening-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=true",
    "listful.security.rate-limit-enabled=true",
    "listful.security.rate-limit-max-requests=3",
    "listful.security.rate-limit-window-seconds=60",
    "listful.security.rate-limit-max-buckets=100",
    "listful.security.trust-forwarded-for=true",
    "listful.security.max-request-body-bytes=128"
})
class SecurityHardeningTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ListRepository listRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired SettingRepository settingRepository;

    @BeforeEach
    void cleanDatabase() {
        itemRepository.deleteAll();
        listRepository.deleteAll();
        userRepository.deleteAll();
        settingRepository.deleteAll();
    }

    @Test
    void sqlInjectionPayloadsDoNotAuthenticateOrResolveShareTokens() throws Exception {
        MockHttpSession owner = register("owner", "correct horse battery staple");
        register("victim", "victim password");
        String listId = createWishList(owner, "Birthday");
        String token = createPublicShare(owner, listId);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("""
                    {"username":"owner' OR '1'='1","password":"victim password"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("bad_credentials"))
            .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());

        mockMvc.perform(get("/api/v1/share/{token}", token + "' OR '1'='1"))
            .andExpect(status().isNotFound());
    }

    @Test
    void sensitiveAnonymousEndpointsAreRateLimitedPerClient() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .header("X-Forwarded-For", "203.0.113.10")
                    .contentType("application/json")
                    .content("{\"username\":\"missing\",\"password\":\"wrong password\"}"))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                .header("X-Forwarded-For", "203.0.113.10")
                .contentType("application/json")
                .content("{\"username\":\"missing\",\"password\":\"wrong password\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("rate_limited"));
    }

    @Test
    void oversizedJsonRequestBodiesAreRejectedBeforeParsing() throws Exception {
        String oversizedUsername = "a".repeat(200);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"%s\",\"password\":\"wrong password\"}".formatted(oversizedUsername)))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("payload_too_large"));
    }

    @Test
    void oversizedJsonRequestBodiesAreRejectedEvenWithoutContentLength() throws Exception {
        SecurityHardeningProperties properties = new SecurityHardeningProperties();
        properties.setMaxRequestBodyBytes(128);
        SecurityHardeningFilter filter = new SecurityHardeningFilter(properties, new ObjectMapper());
        MockHttpServletRequest rawRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        rawRequest.setContentType("application/json");
        rawRequest.setContent("{\"username\":\"%s\"}".formatted("a".repeat(200)).getBytes());
        HttpServletRequest requestWithoutLength = new ContentLengthHidingRequest(rawRequest);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithoutLength, response, (request, servletResponse) -> {
            throw new AssertionError("Oversized body should be rejected before reaching the filter chain.");
        });

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("payload_too_large");
    }

    @Test
    void actualScrapeEndpointIsRateLimited() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/v1/utils/scrape")
                    .header("X-Forwarded-For", "203.0.113.20")
                    .contentType("application/json")
                    .content("{\"url\":\"https://example.test/item\"}"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
        }

        mockMvc.perform(post("/api/v1/utils/scrape")
                .header("X-Forwarded-For", "203.0.113.20")
                .contentType("application/json")
                .content("{\"url\":\"https://example.test/item\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("rate_limited"));
    }

    private MockHttpSession register(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"%s\",\"email\":\"%s@example.test\",\"password\":\"%s\"}"
                    .formatted(username, username, password)))
            .andExpect(status().isCreated())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String createWishList(MockHttpSession session, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lists").session(session)
                .contentType("application/json")
                .content("{\"title\":\"%s\",\"description\":\"\",\"type\":\"WISH\"}".formatted(title)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createPublicShare(MockHttpSession session, String listId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lists/{listId}/public-share", listId).session(session))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.shareToken");
    }

    private static final class ContentLengthHidingRequest extends ContentCachingRequestWrapper {
        private ContentLengthHidingRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }
    }
}
