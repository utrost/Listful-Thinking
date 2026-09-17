package app.listful.sharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.ListShareRepository;
import app.listful.domain.repository.SettingRepository;
import app.listful.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:public-share-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=true"
})
class PublicShareTests {
    @Autowired MockMvc mockMvc;
    @Autowired ItemRepository itemRepository;
    @Autowired ListShareRepository listShareRepository;
    @Autowired ListRepository listRepository;
    @Autowired UserRepository userRepository;
    @Autowired SettingRepository settingRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        itemRepository.deleteAll();
        listShareRepository.deleteAll();
        listRepository.deleteAll();
        userRepository.deleteAll();
        settingRepository.deleteAll();
    }

    @Test
    void ownerCanCreateAndRevokePublicShareToken() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createWishList(owner, "Birthday");

        MvcResult created = mockMvc.perform(post("/api/v1/lists/{listId}/public-share", listId).session(owner))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.listId").value(listId))
            .andExpect(jsonPath("$.publicList").value(true))
            .andExpect(jsonPath("$.shareToken", matchesPattern("[A-Za-z0-9_-]{32,}")))
            .andExpect(jsonPath("$.shareUrl", matchesPattern("/s/[A-Za-z0-9_-]{32,}")))
            .andReturn();

        String token = JsonPath.read(created.getResponse().getContentAsString(), "$.shareToken");
        String storedToken = storedShareTokenFor(listId);
        assertThat(storedToken)
            .startsWith("sha256:")
            .doesNotContain(token)
            .isNotEqualTo(token);

        mockMvc.perform(get("/api/v1/share/{token}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Birthday"));

        mockMvc.perform(delete("/api/v1/lists/{listId}/public-share", listId).session(owner))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/share/{token}", token))
            .andExpect(status().isNotFound());
    }

    @Test
    void publicWishShareExposesSafeFieldsAndAllowsGuestClaimOnce() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createWishList(owner, "Birthday");
        String itemId = createItem(owner, listId, "Book");
        String token = createPublicShare(owner, listId);

        mockMvc.perform(get("/api/v1/share/{token}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").doesNotExist())
            .andExpect(jsonPath("$.ownerEmail").doesNotExist())
            .andExpect(jsonPath("$.title").value("Birthday"))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].id").value(itemId))
            .andExpect(jsonPath("$.items[0].name").value("Book"))
            .andExpect(jsonPath("$.items[0].status").value("OPEN"));

        mockMvc.perform(post("/api/v1/share/{token}/items/{itemId}/claim", token, itemId)
                .contentType("application/json")
                .content("{\"guestName\":\"Annette\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLAIMED"))
            .andExpect(jsonPath("$.reservedByGuest").value("Annette"));

        mockMvc.perform(post("/api/v1/share/{token}/items/{itemId}/claim", token, itemId)
                .contentType("application/json")
                .content("{\"guestName\":\"Martha\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("item_already_claimed"));
    }

    @Test
    void deactivatedSessionIsInvalidatedButPublicShareIsServedAnonymously() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createWishList(owner, "Birthday");
        String token = createPublicShare(owner, listId);
        var user = userRepository.findByUsername("owner").orElseThrow();
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/share/{token}", token).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Birthday"));

        assertThatThrownBy(() -> owner.getAttribute("SPRING_SECURITY_CONTEXT"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentGuestClaimsAllowExactlyOneWinner() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createWishList(owner, "Birthday");
        String itemId = createItem(owner, listId, "Book");
        String token = createPublicShare(owner, listId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ClaimAttempt>> futures = new ArrayList<>();

        for (String guestName : List.of("Annette", "Martha")) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to start concurrent claim");
                }
                MvcResult result = mockMvc.perform(post("/api/v1/share/{token}/items/{itemId}/claim", token, itemId)
                        .contentType("application/json")
                        .content("{\"guestName\":\"%s\"}".formatted(guestName)))
                    .andReturn();
                return new ClaimAttempt(guestName, result.getResponse().getStatus(), result.getResponse().getContentAsString());
            }));
        }

        if (!ready.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Claim workers were not ready");
        }
        start.countDown();
        List<ClaimAttempt> attempts = new ArrayList<>();
        for (Future<ClaimAttempt> future : futures) {
            attempts.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        List<ClaimAttempt> winners = attempts.stream()
            .filter(attempt -> attempt.status() == 200)
            .toList();
        List<ClaimAttempt> losers = attempts.stream()
            .filter(attempt -> attempt.status() == 409)
            .toList();

        org.assertj.core.api.Assertions.assertThat(winners)
            .describedAs("claim attempts: %s", attempts)
            .hasSize(1);
        org.assertj.core.api.Assertions.assertThat(losers)
            .describedAs("claim attempts: %s", attempts)
            .hasSize(1);
        org.assertj.core.api.Assertions.assertThat(losers.get(0).body()).contains("item_already_claimed");
        org.assertj.core.api.Assertions.assertThat(itemRepository.findById(itemId).orElseThrow().getReservedByGuest())
            .isEqualTo(winners.get(0).guestName());
    }

    @Test
    void guestClaimIsOnlyAllowedForItemsBelongingToTheSharedWishList() throws Exception {
        MockHttpSession owner = register("owner");
        String sharedListId = createWishList(owner, "Shared");
        String otherListId = createWishList(owner, "Other");
        String otherItemId = createItem(owner, otherListId, "Other item");
        String token = createPublicShare(owner, sharedListId);

        mockMvc.perform(post("/api/v1/share/{token}/items/{itemId}/claim", token, otherItemId)
                .contentType("application/json")
                .content("{\"guestName\":\"Annette\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanCreateReadOnlyPublicShareModeThatDoesNotAllowGuestClaims() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createWishList(owner, "Read only ideas");
        String itemId = createItem(owner, listId, "Book");

        MvcResult created = mockMvc.perform(post("/api/v1/lists/{listId}/public-share", listId).session(owner)
                .contentType("application/json")
                .content("{\"mode\":\"VIEW\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mode").value("VIEW"))
            .andReturn();
        String token = JsonPath.read(created.getResponse().getContentAsString(), "$.shareToken");

        mockMvc.perform(get("/api/v1/share/{token}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("VIEW"));

        mockMvc.perform(post("/api/v1/share/{token}/items/{itemId}/claim", token, itemId)
                .contentType("application/json")
                .content("{\"guestName\":\"Annette\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void signupModeAllowsGuestsToClaimOpenEventTasks() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createEventList(owner, "Birthday", "2026-10-01T12:00:00Z");
        String itemId = createItem(owner, listId, "Bring salad");
        String token = createPublicShare(owner, listId, "SIGNUP");

        mockMvc.perform(get("/api/v1/share/{token}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("EVENT"))
            .andExpect(jsonPath("$.mode").value("SIGNUP"));

        mockMvc.perform(post("/api/v1/share/{token}/items/{itemId}/claim", token, itemId)
                .contentType("application/json")
                .content("{\"guestName\":\"Annette\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLAIMED"))
            .andExpect(jsonPath("$.reservedByGuest").value("Annette"));
    }

    @Test
    void wishClaimModeIsRejectedForNonWishLists() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createEventList(owner, "Birthday", "2026-10-01T12:00:00Z");

        mockMvc.perform(post("/api/v1/lists/{listId}/public-share", listId).session(owner)
                .contentType("application/json")
                .content("{\"mode\":\"WISH_CLAIM\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void taskLikeListsDefaultToReadOnlyPublicShareMode() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createEventList(owner, "Planning", "2026-10-01T12:00:00Z");

        mockMvc.perform(get("/api/v1/lists/{listId}", listId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publicShareMode").value("VIEW"));

        MvcResult created = mockMvc.perform(post("/api/v1/lists/{listId}/public-share", listId).session(owner))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mode").value("VIEW"))
            .andReturn();
        String token = JsonPath.read(created.getResponse().getContentAsString(), "$.shareToken");

        mockMvc.perform(get("/api/v1/share/{token}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("VIEW"));
    }

    @Test
    void changingListTypeReconcilesExistingPublicShareMode() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createEventList(owner, "Planning", "2026-10-01T12:00:00Z");
        String token = createPublicShare(owner, listId, "SIGNUP");

        mockMvc.perform(put("/api/v1/lists/{listId}", listId).session(owner)
                .contentType("application/json")
                .content("{\"title\":\"Planning\",\"description\":\"\",\"type\":\"WISH\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publicShareMode").value("WISH_CLAIM"));

        mockMvc.perform(get("/api/v1/share/{token}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("WISH"))
            .andExpect(jsonPath("$.mode").value("WISH_CLAIM"));
    }

    @Test
    void legacyRawShareTokensAreAcceptedOnceAndMigratedToHash() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createWishList(owner, "Legacy public link");
        jdbcTemplate.update(
            "update lists set share_token = ?, is_public = 1, public_share_mode = 'WISH_CLAIM' where id = ?",
            "legacy-public-token",
            listId
        );

        mockMvc.perform(get("/api/v1/share/{token}", "legacy-public-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Legacy public link"));

        String storedToken = storedShareTokenFor(listId);
        assertThat(storedToken)
            .startsWith("sha256:")
            .doesNotContain("legacy-public-token")
            .isNotEqualTo("legacy-public-token");
    }

    private record ClaimAttempt(String guestName, int status, String body) {
    }

    private String createPublicShare(MockHttpSession session, String listId) throws Exception {
        return createPublicShare(session, listId, null);
    }

    private String createPublicShare(MockHttpSession session, String listId, String mode) throws Exception {
        String body = mode == null ? null : "{\"mode\":\"%s\"}".formatted(mode);
        var request = post("/api/v1/lists/{listId}/public-share", listId).session(session);
        if (body != null) {
            request.contentType("application/json").content(body);
        }
        MvcResult result = mockMvc.perform(request)
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.shareToken");
    }

    private String storedShareTokenFor(String listId) {
        return jdbcTemplate.queryForObject("select share_token from lists where id = ?", String.class, listId);
    }

    private String createWishList(MockHttpSession session, String title) throws Exception {
        return createTypedList(session, title, "WISH", null);
    }

    private String createEventList(MockHttpSession session, String title, String targetDate) throws Exception {
        return createTypedList(session, title, "EVENT", targetDate);
    }

    private String createTypedList(MockHttpSession session, String title, String type, String targetDate) throws Exception {
        String targetPart = targetDate == null ? "" : ",\"targetDate\":\"%s\"".formatted(targetDate);
        MvcResult result = mockMvc.perform(post("/api/v1/lists").session(session)
                .contentType("application/json")
                .content("{\"title\":\"%s\",\"description\":\"\",\"type\":\"%s\"%s}".formatted(title, type, targetPart)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createItem(MockHttpSession session, String listId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lists/{listId}/items", listId).session(session)
                .contentType("application/json")
                .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
