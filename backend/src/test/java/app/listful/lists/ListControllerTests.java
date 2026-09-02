package app.listful.lists;

import static org.hamcrest.Matchers.hasSize;
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
    "spring.datasource.url=jdbc:sqlite:file:list-controller-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=true"
})
class ListControllerTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ListRepository listRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired ListShareRepository listShareRepository;
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
    void ownerCanCreateListReadItUpdateItAndDeleteIt() throws Exception {
        MockHttpSession owner = register("owner");

        String listId = createList(owner, "Birthday", "Gift ideas", "WISH");

        mockMvc.perform(get("/api/v1/lists").session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(listId))
            .andExpect(jsonPath("$[0].title").value("Birthday"));

        mockMvc.perform(get("/api/v1/lists/{id}", listId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Birthday"));

        mockMvc.perform(put("/api/v1/lists/{id}", listId).session(owner)
                .contentType("application/json")
                .content("""
                    {"title":"Birthday 2027","description":"Updated ideas","type":"EVENT","targetDate":"2027-01-01T00:00:00Z"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Birthday 2027"))
            .andExpect(jsonPath("$.type").value("EVENT"));

        mockMvc.perform(delete("/api/v1/lists/{id}", listId).session(owner))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/lists").session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ownerCanCreateTodoListWithoutListTargetDate() throws Exception {
        MockHttpSession owner = register("owner");

        String listId = createList(owner, "Next actions", "Things to do", "TODO");

        mockMvc.perform(get("/api/v1/lists/{id}", listId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Next actions"))
            .andExpect(jsonPath("$.type").value("TODO"));
    }

    @Test
    void usersCannotSeeUpdateOrDeleteEachOthersLists() throws Exception {
        MockHttpSession owner = register("owner");
        MockHttpSession other = register("other");
        String ownerListId = createList(owner, "Private", "Mine", "WISH");

        mockMvc.perform(get("/api/v1/lists").session(other))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/lists/{id}", ownerListId).session(other))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/lists/{id}", ownerListId).session(other)
                .contentType("application/json")
                .content("""
                    {"title":"Stolen","description":"Nope","type":"WISH"}
                    """))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/lists/{id}", ownerListId).session(other))
            .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanCloneAListWithItemsButWithoutPublicTokenOrInternalShares() throws Exception {
        MockHttpSession owner = register("owner");
        register("helper");
        String sourceListId = createList(owner, "Birthday", "Gift ideas", "WISH");
        String sourceItemId = createDetailedItem(owner, sourceListId);

        mockMvc.perform(post("/api/v1/lists/{id}/public-share", sourceListId).session(owner))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.shareToken").isNotEmpty());
        mockMvc.perform(post("/api/v1/lists/{id}/shares", sourceListId).session(owner)
                .contentType("application/json")
                .content("{\"username\":\"helper\",\"permission\":\"CONTRIBUTE\"}"))
            .andExpect(status().isCreated());

        MvcResult cloneResult = mockMvc.perform(post("/api/v1/lists/{id}/clone", sourceListId).session(owner)
                .contentType("application/json")
                .content("{\"title\":\"Trip copy\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Trip copy"))
            .andExpect(jsonPath("$.description").value("Gift ideas"))
            .andExpect(jsonPath("$.type").value("WISH"))
            .andExpect(jsonPath("$.targetDate").doesNotExist())
            .andExpect(jsonPath("$.publicList").value(false))
            .andExpect(jsonPath("$.shareToken").doesNotExist())
            .andReturn();
        String cloneListId = JsonPath.read(cloneResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/lists/{listId}/items", cloneListId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(org.hamcrest.Matchers.not(sourceItemId)))
            .andExpect(jsonPath("$[0].name").value("Camera"))
            .andExpect(jsonPath("$[0].description").value("Bring a camera"))
            .andExpect(jsonPath("$[0].url").value("https://shop.test/camera"))
            .andExpect(jsonPath("$[0].imageUrl").value("https://shop.test/camera.jpg"))
            .andExpect(jsonPath("$[0].price").value(29.95))
            .andExpect(jsonPath("$[0].status").value("PURCHASED"))
            .andExpect(jsonPath("$[0].dueDate").doesNotExist())
            .andExpect(jsonPath("$[0].quantity").doesNotExist())
            .andExpect(jsonPath("$[0].category").doesNotExist());

        mockMvc.perform(get("/api/v1/lists/{id}/shares", cloneListId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void nonOwnersCannotCloneListsByGuessingIds() throws Exception {
        MockHttpSession owner = register("owner");
        MockHttpSession other = register("other");
        String sourceListId = createList(owner, "Private", "Mine", "WISH");

        mockMvc.perform(post("/api/v1/lists/{id}/clone", sourceListId).session(other)
                .contentType("application/json")
                .content("{\"title\":\"Stolen copy\"}"))
            .andExpect(status().isNotFound());
    }

    private String createList(MockHttpSession session, String title, String description, String type) throws Exception {
        return createList(session, title, description, type, null);
    }

    private String createList(MockHttpSession session, String title, String description, String type, String targetDate) throws Exception {
        String targetJson = targetDate == null ? "" : ",\"targetDate\":\"%s\"".formatted(targetDate);
        MvcResult result = mockMvc.perform(post("/api/v1/lists").session(session)
                .contentType("application/json")
                .content("{\"title\":\"%s\",\"description\":\"%s\",\"type\":\"%s\"%s}"
                    .formatted(title, description, type, targetJson)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createDetailedItem(MockHttpSession session, String listId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lists/{listId}/items", listId).session(session)
                .contentType("application/json")
                .content("""
                    {"name":"Camera","description":"Bring a camera","url":"https://shop.test/camera","imageUrl":"https://shop.test/camera.jpg","price":29.95,"status":"PURCHASED"}
                    """))
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
