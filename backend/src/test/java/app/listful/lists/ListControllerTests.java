package app.listful.lists;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.repository.ListRepository;
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
    "spring.datasource.url=jdbc:sqlite:file:list-controller-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=true"
})
class ListControllerTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ListRepository listRepository;
    @Autowired SettingRepository settingRepository;

    @BeforeEach
    void cleanDatabase() {
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

    private String createList(MockHttpSession session, String title, String description, String type) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/lists").session(session)
                .contentType("application/json")
                .content("{\"title\":\"%s\",\"description\":\"%s\",\"type\":\"%s\"}"
                    .formatted(title, description, type)))
            .andExpect(status().isCreated())
            .andReturn();
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
