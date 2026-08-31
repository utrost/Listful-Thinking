package app.listful.items;

import static org.hamcrest.Matchers.hasSize;
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

    @BeforeEach
    void cleanDatabase() {
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
