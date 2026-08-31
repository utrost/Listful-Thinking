package app.listful.sharing;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.ListShareRepository;
import app.listful.domain.repository.SettingRepository;
import app.listful.domain.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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

    private String createPublicShare(MockHttpSession session, String listId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lists/{listId}/public-share", listId).session(session))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.shareToken");
    }

    private String createWishList(MockHttpSession session, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lists").session(session)
                .contentType("application/json")
                .content("{\"title\":\"%s\",\"description\":\"\",\"type\":\"WISH\"}".formatted(title)))
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
