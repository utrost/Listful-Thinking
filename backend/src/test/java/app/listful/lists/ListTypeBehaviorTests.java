package app.listful.lists;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    "spring.datasource.url=jdbc:sqlite:file:list-type-behavior-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "listful.registration-enabled=true"
})
class ListTypeBehaviorTests {
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
    void eventListsRequireATargetDate() throws Exception {
        MockHttpSession owner = register("owner");

        mockMvc.perform(post("/api/v1/lists").session(owner)
                .contentType("application/json")
                .content("""
                    {"title":"Party","description":"","type":"EVENT"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"))
            .andExpect(jsonPath("$.message").value("Event lists require a target date."));
    }

    @Test
    void wishAndChoreListsCannotSetAListTargetDate() throws Exception {
        MockHttpSession owner = register("owner");

        mockMvc.perform(post("/api/v1/lists").session(owner)
                .contentType("application/json")
                .content("""
                    {"title":"Chores","description":"","type":"CHORE","targetDate":"2027-01-01T00:00:00Z"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"))
            .andExpect(jsonPath("$.message").value("Only event lists can have a target date."));
    }

    @Test
    void choreItemsRejectShoppingFieldsButAcceptDueDateAndRecurrence() throws Exception {
        MockHttpSession owner = register("owner");
        String choreListId = createList(owner, "Weekly chores", "CHORE", null);

        mockMvc.perform(post("/api/v1/lists/{listId}/items", choreListId).session(owner)
                .contentType("application/json")
                .content("""
                    {"name":"Vacuum","url":"https://example.test/vacuum"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"))
            .andExpect(jsonPath("$.message").value("Shopping fields are only allowed on wish lists."));

        mockMvc.perform(post("/api/v1/lists/{listId}/items", choreListId).session(owner)
                .contentType("application/json")
                .content("""
                    {"name":"Vacuum","dueDate":"2027-01-01T00:00:00Z","recurrenceRule":"FREQ=WEEKLY"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Vacuum"))
            .andExpect(jsonPath("$.dueDate").value("2027-01-01T00:00:00Z"))
            .andExpect(jsonPath("$.recurrenceRule").value("FREQ=WEEKLY"));
    }

    @Test
    void eventItemsRejectShoppingAndRecurrenceFieldsButAcceptDueDate() throws Exception {
        MockHttpSession owner = register("owner");
        String eventListId = createList(owner, "Birthday", "EVENT", "2027-02-01T00:00:00Z");

        mockMvc.perform(post("/api/v1/lists/{listId}/items", eventListId).session(owner)
                .contentType("application/json")
                .content("""
                    {"name":"Book venue","price":100,"recurrenceRule":"FREQ=DAILY"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"));

        mockMvc.perform(post("/api/v1/lists/{listId}/items", eventListId).session(owner)
                .contentType("application/json")
                .content("""
                    {"name":"Book venue","dueDate":"2027-01-15T00:00:00Z"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Book venue"))
            .andExpect(jsonPath("$.dueDate").value("2027-01-15T00:00:00Z"));
    }

    @Test
    void wishItemsKeepShoppingFields() throws Exception {
        MockHttpSession owner = register("owner");
        String wishListId = createList(owner, "Birthday wishes", "WISH", null);

        mockMvc.perform(post("/api/v1/lists/{listId}/items", wishListId).session(owner)
                .contentType("application/json")
                .content("""
                    {"name":"Book","url":"https://example.test/book","imageUrl":"https://example.test/book.jpg","price":19.99}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.url").value("https://example.test/book"))
            .andExpect(jsonPath("$.imageUrl").value("https://example.test/book.jpg"))
            .andExpect(jsonPath("$.price").value(19.99));

        mockMvc.perform(get("/api/v1/lists/{listId}/items", wishListId).session(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));
    }

    private String createList(MockHttpSession session, String title, String type, String targetDate) throws Exception {
        String targetDateJson = targetDate == null ? "" : ",\"targetDate\":\"" + targetDate + "\"";
        MvcResult result = mockMvc.perform(post("/api/v1/lists").session(session)
                .contentType("application/json")
                .content("{\"title\":\"%s\",\"description\":\"\",\"type\":\"%s\"%s}"
                    .formatted(title, type, targetDateJson)))
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
