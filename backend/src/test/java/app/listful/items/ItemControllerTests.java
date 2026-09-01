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
    void todoItemsAllowDueDateForReminderNotificationsButRejectShoppingFieldsAndRecurrence() throws Exception {
        MockHttpSession owner = register("owner");
        String listId = createList(owner, "Next actions", "TODO");

        mockMvc.perform(post("/api/v1/lists/{listId}/items", listId).session(owner)
                .contentType("application/json")
                .content("""
                    {"name":"Call optician","dueDate":"2027-01-01T09:30:00Z"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Call optician"))
            .andExpect(jsonPath("$.dueDate").value("2027-01-01T09:30:00Z"));

        mockMvc.perform(post("/api/v1/lists/{listId}/items", listId).session(owner)
                .contentType("application/json")
                .content("""
                    {"name":"Buy thing","url":"https://shop.test/thing"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));

        mockMvc.perform(post("/api/v1/lists/{listId}/items", listId).session(owner)
                .contentType("application/json")
                .content("""
                    {"name":"Repeat thing","dueDate":"2027-01-01T09:30:00Z","recurrenceRule":"FREQ=WEEKLY"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void todoGroceryChoreAndEventItemsCanBeMarkedDoneButRejectWishlistStatuses() throws Exception {
        MockHttpSession owner = register("owner");
        String todoId = createList(owner, "Next actions", "TODO");
        String groceryId = createList(owner, "Groceries", "GROCERY");
        String choreId = createList(owner, "Chores", "CHORE");
        String eventId = createEventList(owner, "Trip");

        String todoItem = createItem(owner, todoId, "Call optician");
        String groceryItem = createItem(owner, groceryId, "Oat milk");
        String choreItem = createItem(owner, choreId, "Water plants");
        String eventItem = createItem(owner, eventId, "Pack bag");

        for (String itemId : java.util.List.of(todoItem, groceryItem, choreItem, eventItem)) {
            mockMvc.perform(put("/api/v1/items/{itemId}", itemId).session(owner)
                    .contentType("application/json")
                    .content("{\"name\":\"Done item\",\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
        }

        mockMvc.perform(put("/api/v1/items/{itemId}", todoItem).session(owner)
                .contentType("application/json")
                .content("{\"name\":\"Bought task\",\"status\":\"PURCHASED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void ownerCanClearCompletedGroceryItemsOnly() throws Exception {
        MockHttpSession owner = register("owner");
        String groceryId = createList(owner, "Groceries", "GROCERY");
        String todoId = createList(owner, "Next actions", "TODO");
        String openMilk = createItem(owner, groceryId, "Oat milk");
        String boughtApples = createItem(owner, groceryId, "Apples");
        String doneTodo = createItem(owner, todoId, "Call optician");

        markDone(owner, boughtApples, "Apples");
        markDone(owner, doneTodo, "Call optician");

        mockMvc.perform(delete("/api/v1/lists/{listId}/items/completed", groceryId).session(owner))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/lists/{listId}/items", groceryId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(openMilk));

        mockMvc.perform(get("/api/v1/lists/{listId}/items", todoId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(doneTodo));
    }

    @Test
    void clearCompletedIsOnlyForGroceryLists() throws Exception {
        MockHttpSession owner = register("owner");
        String todoId = createList(owner, "Next actions", "TODO");
        String todoItem = createItem(owner, todoId, "Call optician");
        markDone(owner, todoItem, "Call optician");

        mockMvc.perform(delete("/api/v1/lists/{listId}/items/completed", todoId).session(owner))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void wishItemsKeepClaimAndPurchaseStatusesButRejectDone() throws Exception {
        MockHttpSession owner = register("owner");
        String wishListId = createList(owner, "Birthday", "WISH");
        String itemId = createItem(owner, wishListId, "Book");

        mockMvc.perform(put("/api/v1/items/{itemId}", itemId).session(owner)
                .contentType("application/json")
                .content("{\"name\":\"Book\",\"status\":\"PURCHASED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PURCHASED"));

        mockMvc.perform(put("/api/v1/items/{itemId}", itemId).session(owner)
                .contentType("application/json")
                .content("{\"name\":\"Book\",\"status\":\"DONE\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));
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

    private void markDone(MockHttpSession session, String itemId, String name) throws Exception {
        mockMvc.perform(put("/api/v1/items/{itemId}", itemId).session(session)
                .contentType("application/json")
                .content("{\"name\":\"%s\",\"status\":\"DONE\"}".formatted(name)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DONE"));
    }

    private String createList(MockHttpSession session, String title) throws Exception {
        return createList(session, title, "WISH");
    }

    private String createList(MockHttpSession session, String title, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lists").session(session)
                .contentType("application/json")
                .content("{\"title\":\"%s\",\"description\":\"\",\"type\":\"%s\"}".formatted(title, type)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createEventList(MockHttpSession session, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lists").session(session)
                .contentType("application/json")
                .content("{\"title\":\"%s\",\"description\":\"\",\"type\":\"EVENT\",\"targetDate\":\"2027-01-01T00:00:00Z\"}".formatted(title)))
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
