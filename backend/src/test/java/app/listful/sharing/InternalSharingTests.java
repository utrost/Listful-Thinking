package app.listful.sharing;

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
    "spring.datasource.url=jdbc:sqlite:file:list-sharing-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=true"
})
class InternalSharingTests {
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
    void ownerCanShareListWithRegisteredUserAndRevokeAccess() throws Exception {
        MockHttpSession owner = register("owner");
        MockHttpSession shared = register("shared");
        String listId = createWishList(owner, "Shared birthday");
        String itemId = createItem(owner, listId, "Gift idea");

        mockMvc.perform(post("/api/v1/lists/{listId}/shares", listId).session(owner)
                .contentType("application/json")
                .content("{\"username\":\"shared\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("shared"))
            .andExpect(jsonPath("$.permission").value("READ"))
            .andExpect(jsonPath("$.listId").value(listId));

        mockMvc.perform(get("/api/v1/lists/{listId}/shares", listId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].username").value("shared"))
            .andExpect(jsonPath("$[0].permission").value("READ"));

        mockMvc.perform(get("/api/v1/lists/{id}", listId).session(shared))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Shared birthday"));

        mockMvc.perform(get("/api/v1/lists").session(shared))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(listId))
            .andExpect(jsonPath("$[0].access").value("READ"));

        mockMvc.perform(get("/api/v1/lists/{listId}/items", listId).session(shared))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(itemId));

        mockMvc.perform(put("/api/v1/lists/{id}", listId).session(shared)
                .contentType("application/json")
                .content("{\"title\":\"Changed\",\"type\":\"WISH\"}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/lists/{listId}/items", listId).session(shared)
                .contentType("application/json")
                .content("{\"name\":\"Unauthorized\"}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/lists/{listId}/shares/{username}", listId, "shared").session(owner))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/lists/{id}", listId).session(shared))
            .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanGrantContributorPermissionForSharedItemWork() throws Exception {
        MockHttpSession owner = register("owner");
        MockHttpSession contributor = register("contributor");
        register("reader");
        String listId = createWishList(owner, "Household tasks");

        mockMvc.perform(post("/api/v1/lists/{listId}/shares", listId).session(owner)
                .contentType("application/json")
                .content("{\"username\":\"contributor\",\"permission\":\"CONTRIBUTE\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("contributor"))
            .andExpect(jsonPath("$.permission").value("CONTRIBUTE"));

        mockMvc.perform(get("/api/v1/lists").session(contributor))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(listId))
            .andExpect(jsonPath("$[0].access").value("CONTRIBUTE"));

        MvcResult created = mockMvc.perform(post("/api/v1/lists/{listId}/items", listId).session(contributor)
                .contentType("application/json")
                .content("{\"name\":\"Bring cake\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Bring cake"))
            .andReturn();
        String itemId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/v1/items/{itemId}", itemId).session(contributor)
                .contentType("application/json")
                .content("{\"name\":\"Bring chocolate cake\",\"status\":\"OPEN\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Bring chocolate cake"));

        mockMvc.perform(put("/api/v1/lists/{id}", listId).session(contributor)
                .contentType("application/json")
                .content("{\"title\":\"Changed\",\"type\":\"WISH\"}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/items/{itemId}", itemId).session(contributor))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/lists/{listId}/shares", listId).session(owner)
                .contentType("application/json")
                .content("{\"username\":\"reader\",\"permission\":\"READ\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.permission").value("READ"));
    }

    @Test
    void nonOwnersCannotManageShares() throws Exception {
        MockHttpSession owner = register("owner");
        MockHttpSession other = register("other");
        register("shared");
        String listId = createWishList(owner, "Private");

        mockMvc.perform(post("/api/v1/lists/{listId}/shares", listId).session(other)
                .contentType("application/json")
                .content("{\"username\":\"shared\"}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/lists/{listId}/shares", listId).session(other))
            .andExpect(status().isNotFound());
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
