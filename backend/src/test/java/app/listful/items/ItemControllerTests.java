package app.listful.items;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.SettingRepository;
import app.listful.domain.repository.UserRepository;
import app.listful.scraping.ScraperService;
import app.listful.scraping.dto.ScrapeResponse;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:item-controller-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=true"
})
class ItemControllerTests {
    @Autowired MockMvc mockMvc;
    @Autowired ItemRepository itemRepository;
    @Autowired ListRepository listRepository;
    @Autowired UserRepository userRepository;
    @Autowired SettingRepository settingRepository;
    @MockBean ScraperService scraperService;

    @BeforeEach
    void cleanDatabase() {
        reset(scraperService);
        itemRepository.deleteAll();
        listRepository.deleteAll();
        userRepository.deleteAll();
        settingRepository.deleteAll();
    }

    @Test
    void ownerCanCreateListItemsReadThemUpdateAndDeleteThem() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createList(owner, "Birthday");

        String itemId = createItem(owner, listId, "Camera strap");

        mockMvc.perform(get("/api/v1/lists/{listId}/items", listId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(itemId))
            .andExpect(jsonPath("$[0].name").value("Camera strap"))
            .andExpect(jsonPath("$[0].status").value("OPEN"));

        mockMvc.perform(put("/api/v1/items/{itemId}", itemId).session(owner)
                .contentType("application/json")
                .content("""
                    {"name":"Leather camera strap","url":"https://example.test/strap","imageUrl":"https://example.test/strap.jpg","price":29.90,"status":"PURCHASED","dueDate":"2027-01-01T00:00:00Z","recurrenceRule":null}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Leather camera strap"))
            .andExpect(jsonPath("$.status").value("PURCHASED"))
            .andExpect(jsonPath("$.price").value(29.90));

        mockMvc.perform(delete("/api/v1/items/{itemId}", itemId).session(owner))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/lists/{listId}/items", listId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void usersCannotReadOrMutateItemsFromListsTheyDoNotOwn() throws Exception {
        MockHttpSession owner = register("owner");
        MockHttpSession other = register("other");
        String listId = createList(owner, "Private");
        String itemId = createItem(owner, listId, "Secret gift");

        mockMvc.perform(get("/api/v1/lists/{listId}/items", listId).session(other))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/items/{itemId}", itemId).session(other)
                .contentType("application/json")
                .content("""
                    {"name":"Stolen","status":"PURCHASED"}
                    """))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/items/{itemId}", itemId).session(other))
            .andExpect(status().isNotFound());
    }

    @Test
    void creatingWishItemWithOnlyUrlCreatesPlaceholderAndEnrichesMetadataAsynchronously() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createList(owner, "Birthday");
        CountDownLatch scraperStarted = new CountDownLatch(1);
        CountDownLatch releaseScraper = new CountDownLatch(1);
        doAnswer(invocation -> {
            scraperStarted.countDown();
            releaseScraper.await(2, TimeUnit.SECONDS);
            return new ScrapeResponse("Fetched camera", "Fetched description", "https://shop.test/camera.jpg", new BigDecimal("49.95"));
        }).when(scraperService).scrape(eq("https://shop.test/camera"));

        MvcResult result = mockMvc.perform(post("/api/v1/lists/{listId}/items", listId).session(owner)
                .contentType("application/json")
                .content("{\"url\":\"https://shop.test/camera\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Loading metadata…"))
            .andExpect(jsonPath("$.url").value("https://shop.test/camera"))
            .andExpect(jsonPath("$.imageUrl").doesNotExist())
            .andReturn();
        String itemId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        if (!scraperStarted.await(2, TimeUnit.SECONDS)) {
            throw new AssertionError("scraper did not start asynchronously");
        }
        releaseScraper.countDown();

        awaitItemName(itemId, "Fetched camera");
        mockMvc.perform(get("/api/v1/lists/{listId}/items", listId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Fetched camera"))
            .andExpect(jsonPath("$[0].description").value("Fetched description"))
            .andExpect(jsonPath("$[0].imageUrl").value("https://shop.test/camera.jpg"))
            .andExpect(jsonPath("$[0].price").value(49.95));
    }

    @Test
    void wishItemWithNameAndUrlFetchesBlankMetadataButPreservesUserFields() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createList(owner, "Birthday");
        doAnswer(invocation -> new ScrapeResponse("Fetched title", "Fetched description", "https://shop.test/fetched.jpg", new BigDecimal("19.95")))
            .when(scraperService).scrape(eq("https://shop.test/named"));

        MvcResult result = mockMvc.perform(post("/api/v1/lists/{listId}/items", listId).session(owner)
                .contentType("application/json")
                .content("""
                    {"name":"User title","description":"User description","url":"https://shop.test/named","imageUrl":"https://shop.test/user.jpg"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("User title"))
            .andExpect(jsonPath("$.description").value("User description"))
            .andExpect(jsonPath("$.imageUrl").value("https://shop.test/user.jpg"))
            .andReturn();
        String itemId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        awaitItemPrice(itemId, new BigDecimal("19.95"));
        mockMvc.perform(get("/api/v1/lists/{listId}/items", listId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("User title"))
            .andExpect(jsonPath("$[0].description").value("User description"))
            .andExpect(jsonPath("$[0].imageUrl").value("https://shop.test/user.jpg"))
            .andExpect(jsonPath("$[0].price").value(19.95));
    }

    @Test
    void urlOnlyEnrichmentFailureLeavesPlaceholderItem() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createList(owner, "Birthday");
        doThrow(new RuntimeException("offline")).when(scraperService).scrape(eq("https://shop.test/offline"));

        MvcResult result = mockMvc.perform(post("/api/v1/lists/{listId}/items", listId).session(owner)
                .contentType("application/json")
                .content("{\"url\":\"https://shop.test/offline\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Loading metadata…"))
            .andReturn();
        String itemId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        Thread.sleep(Duration.ofMillis(200).toMillis());
        mockMvc.perform(get("/api/v1/lists/{listId}/items", listId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(itemId))
            .andExpect(jsonPath("$[0].name").value("Loading metadata…"))
            .andExpect(jsonPath("$[0].url").value("https://shop.test/offline"));
    }

    private void awaitItemName(String itemId, String expectedName) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            String currentName = itemRepository.findById(itemId).orElseThrow().getName();
            if (expectedName.equals(currentName)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("item was not enriched within timeout");
    }

    private void awaitItemPrice(String itemId, BigDecimal expectedPrice) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            BigDecimal currentPrice = itemRepository.findById(itemId).orElseThrow().getPrice();
            if (currentPrice != null && expectedPrice.compareTo(currentPrice) == 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("item price was not enriched within timeout");
    }

    private String createItem(MockHttpSession session, String listId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lists/{listId}/items", listId).session(session)
                .contentType("application/json")
                .content("{\"name\":\"%s\"}".formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createList(MockHttpSession session, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lists").session(session)
                .contentType("application/json")
                .content("{\"title\":\"%s\",\"description\":\"\",\"type\":\"WISH\"}".formatted(title)))
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
