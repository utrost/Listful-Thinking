package app.listful.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.*;
import app.listful.domain.repository.*;
import app.listful.domain.enums.ItemStatus;
import app.listful.notifications.ReminderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:planning-lifecycle-test?mode=memory&cache=shared",
    "listful.registration-enabled=true", "listful.security.rate-limit-enabled=false"
})
class PlanningLifecycleTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ItemRepository items;
    @Autowired ListRepository lists;
    @Autowired ListShareRepository shares;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired ReminderService reminders;
    @MockBean Clock clock;
    final Instant now = Instant.parse("2026-03-29T10:00:00Z");
    @BeforeEach void resetData() {
        items.deleteAll(); shares.deleteAll(); lists.deleteAll(); users.deleteAll();
        when(clock.withZone(any())).thenAnswer(call -> Clock.fixed(now, call.getArgument(0)));
    }
    MockHttpSession user(String name) throws Exception {
        return (MockHttpSession)mvc.perform(post("/api/v1/auth/register").contentType("application/json")
            .content(json.writeValueAsString(Map.of("username", name, "password", "a-safe-test-password"))))
            .andExpect(status().isCreated()).andReturn().getRequest().getSession(false);
    }
    JsonNode send(MockHttpSession actor, String path, Object body, int statusCode) throws Exception {
        return json.readTree(mvc.perform(post(path).session(actor).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().is(statusCode)).andReturn().getResponse().getContentAsString());
    }
    JsonNode list(MockHttpSession actor, String name, String type) throws Exception {
        var body = new HashMap<String,Object>(Map.of("title",name,"type",type));
        if (type.equals("EVENT")) body.put("targetDate","2026-03-29T12:00:00Z");
        return send(actor,"/api/v1/lists",body,201);
    }
    JsonNode item(MockHttpSession actor, String listId, String name, String date) throws Exception {
        var body = new HashMap<String,Object>(Map.of("name",name)); if (date != null) body.put("dueDate",date);
        return send(actor,"/api/v1/lists/"+listId+"/items",body,201);
    }
    String id(JsonNode node) { return node.path("id").asText(); }
    JsonNode read(MockHttpSession actor, String path) throws Exception {
        return json.readTree(mvc.perform(get(path).session(actor)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void agendaUsesLocalDayBoundariesIncludingTheSpringDstDay() throws Exception {
        var owner=user("owner"); var list=id(list(owner,"DST tasks","TODO"));
        item(owner,list,"Yesterday","2026-03-28T22:59:59Z");
        item(owner,list,"Local midnight","2026-03-28T23:00:00Z");
        item(owner,list,"Tomorrow midnight","2026-03-29T22:00:00Z");
        item(owner,list,"Outside window","2026-04-05T22:00:00Z");
        var agenda=read(owner,"/api/v1/overview?zone=Europe/Berlin");
        assertThat(agenda.path("date").asText()).isEqualTo("2026-03-29");
        assertThat(agenda.path("overdue").size()).isEqualTo(1);
        assertThat(agenda.path("today").get(0).path("name").asText()).isEqualTo("Local midnight");
        assertThat(agenda.path("upcoming").size()).isEqualTo(1);
        mvc.perform(get("/api/v1/overview?zone=not-a-timezone").session(owner)).andExpect(status().isBadRequest());
    }

    @Test void agendaIncludesOnlyAccessibleActiveOpenWorkAndEvents() throws Exception {
        var owner=user("owner"); var reader=user("reader"); var other=user("other");
        var shared=id(list(owner,"Shared jobs","TODO")); item(owner,shared,"Read this","2026-03-29T12:00:00Z");
        send(owner,"/api/v1/lists/"+shared+"/shares",Map.of("username","reader","permission","READ"),201);
        var privateList=id(list(owner,"Private jobs","TODO")); item(owner,privateList,"Secret","2026-03-29T12:00:00Z");
        var event=id(list(reader,"Picnic","EVENT"));
        var completed=item(reader,event,"Already done","2026-03-29T12:00:00Z");
        var entity=items.findById(id(completed)).orElseThrow(); entity.setStatus(ItemStatus.DONE);items.save(entity);
        var agenda=read(reader,"/api/v1/overview?zone=UTC");
        assertThat(agenda.path("today").size()).isEqualTo(2);
        assertThat(agenda.toString()).contains("Read this","READ","Picnic").doesNotContain("Secret","Already done");
        assertThat(read(other,"/api/v1/overview?zone=UTC").path("today").size()).isZero();
        send(owner,"/api/v1/lists/"+shared+"/archive",Map.of("archived",true),200);
        assertThat(read(reader,"/api/v1/overview?zone=UTC").toString()).doesNotContain("Read this");
        mvc.perform(get("/api/v1/lists/"+shared).session(reader)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/overview")).andExpect(status().isForbidden());
    }

    @Test void itemTrashSurvivesRequestsPreservesDataAndRejectsOtherUsersAndStaleEdits() throws Exception {
        var owner=user("owner"); var other=user("other"); var list=id(list(owner,"Jobs","TODO"));
        var item=item(owner,list,"Keep the notes","2026-03-29T12:00:00Z");var itemId=id(item);
        mvc.perform(delete("/api/v1/items/"+itemId).session(owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/items/"+itemId).session(owner)).andExpect(status().isNotFound());
        assertThat(read(owner,"/api/v1/trash/items").get(0).path("id").asText()).isEqualTo(itemId);
        mvc.perform(post("/api/v1/items/"+itemId+"/restore").session(other)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/items/"+itemId+"/restore").session(owner)).andExpect(status().isOk());
        var restored=read(owner,"/api/v1/items/"+itemId);
        assertThat(restored.path("name").asText()).isEqualTo("Keep the notes");
        assertThat(restored.path("dueDate").asText()).isEqualTo(item.path("dueDate").asText());
        assertThat(restored.path("version").asLong()).isGreaterThan(item.path("version").asLong());
        mvc.perform(put("/api/v1/items/"+itemId).session(owner).header("If-Match","\"0\"").contentType("application/json").content("{\"name\":\"Stale overwrite\"}"))
            .andExpect(status().isConflict());
        assertThat(read(owner,"/api/v1/trash/items").size()).isZero();
    }

    @Test void deletingAndRestoringAListKeepsItemsButDoesNotResurrectPublicLinks() throws Exception {
        var owner=user("owner"); var other=user("other"); var list=id(list(owner,"Party","EVENT"));
        var task=id(item(owner,list,"Bring snacks",null));
        var token=send(owner,"/api/v1/lists/"+list+"/public-share",Map.of("mode","SIGNUP"),201).path("shareToken").asText();
        mvc.perform(delete("/api/v1/lists/"+list).session(owner)).andExpect(status().isNoContent());
        assertThat(read(owner,"/api/v1/lists/library?state=trash").get(0).path("id").asText()).isEqualTo(list);
        assertThat(read(other,"/api/v1/lists/library?state=trash").size()).isZero();
        mvc.perform(post("/api/v1/lists/"+list+"/restore").session(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/lists/"+list+"/items").session(owner)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/lists/"+list+"/restore").session(owner)).andExpect(status().isOk()).andExpect(jsonPath("$.publicList").value(false));
        assertThat(read(owner,"/api/v1/lists/"+list+"/items").get(0).path("id").asText()).isEqualTo(task);
        mvc.perform(get("/api/v1/share/"+token)).andExpect(status().isNotFound());
    }

    @Test void archivingPausesRemindersAndWritesButRestorationPreservesTheList() throws Exception {
        var owner=user("owner"); var list=id(list(owner,"Paused chores","CHORE"));
        var task=id(item(owner,list,"Do this","2026-03-29T11:00:00Z"));
        send(owner,"/api/v1/lists/"+list+"/archive",Map.of("archived",true),200);
        assertThat(read(owner,"/api/v1/lists").size()).isZero();
        assertThat(read(owner,"/api/v1/lists/library?state=archive").size()).isEqualTo(1);
        assertThat(read(owner,"/api/v1/lists/"+list+"/items").size()).isEqualTo(1);
        reminders.processDueReminders(now);assertThat(notifications.count()).isZero();
        mvc.perform(delete("/api/v1/items/"+task).session(owner)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/lists/"+list+"/public-share").session(owner).contentType("application/json").content("{\"mode\":\"VIEW\"}"))
            .andExpect(status().isConflict());
        send(owner,"/api/v1/lists/"+list+"/archive",Map.of("archived",false),200);
        assertThat(read(owner,"/api/v1/lists").size()).isEqualTo(1);
        reminders.processDueReminders(now);assertThat(notifications.count()).isEqualTo(1);
    }

    @Test void templatesCreateFreshPrivateCopiesAndNeverNotifyOrAcceptSharing() throws Exception {
        var owner=user("owner");var other=user("other");var source=id(list(owner,"Weekly maintenance","CHORE"));
        var original=send(owner,"/api/v1/lists/"+source+"/items",Map.of("name","Check filter","dueDate","2026-03-29T11:00:00Z","recurrenceRule","FREQ=WEEKLY","ownerLabel","Alex"),201);
        var originalEntity=items.findById(id(original)).orElseThrow();originalEntity.setLastCompletedAt(now.minusSeconds(86400));originalEntity.setStatus(ItemStatus.DONE);items.save(originalEntity);
        var template=send(owner,"/api/v1/lists/"+source+"/template",Map.of("title","Maintenance blueprint"),201);var templateId=id(template);
        assertThat(template.path("template").asBoolean()).isTrue();
        assertThat(read(owner,"/api/v1/lists").size()).isEqualTo(1);
        assertThat(read(other,"/api/v1/lists/library?state=templates").size()).isZero();
        mvc.perform(post("/api/v1/lists/"+templateId+"/instantiate").session(other).contentType("application/json").content("{\"title\":\"Stolen\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/lists/"+templateId+"/public-share").session(owner).contentType("application/json").content("{\"mode\":\"VIEW\"}"))
            .andExpect(status().isConflict());
        var fresh=send(owner,"/api/v1/lists/"+templateId+"/instantiate",Map.of("title","This week"),201);
        assertThat(fresh.path("template").asBoolean()).isFalse();assertThat(fresh.path("publicList").asBoolean()).isFalse();
        var copied=read(owner,"/api/v1/lists/"+id(fresh)+"/items").get(0);
        assertThat(copied.path("id").asText()).isNotEqualTo(id(original));
        assertThat(copied.path("status").asText()).isEqualTo("OPEN");
        assertThat(copied.path("dueDate").isNull()).isTrue();assertThat(copied.path("lastCompletedAt").isNull()).isTrue();
        assertThat(copied.path("recurrenceRule").asText()).isEqualTo("FREQ=WEEKLY");assertThat(copied.path("ownerLabel").asText()).isEqualTo("Alex");
        assertThat(read(owner,"/api/v1/lists/"+id(fresh)+"/shares").size()).isZero();
        reminders.processDueReminders(now);assertThat(notifications.count()).isZero();
    }

    @Test void eventTemplatesRequireNewEventDateAndDiscardOldReservations() throws Exception {
        var owner=user("owner");var source=id(list(owner,"Old party","EVENT"));var task=item(owner,source,"Bring cake",null);
        var entity=items.findById(id(task)).orElseThrow();entity.claimForGuest("Old guest");items.save(entity);
        var template=id(send(owner,"/api/v1/lists/"+source+"/template",Map.of("title","Party blueprint"),201));
        assertThat(read(owner,"/api/v1/lists/"+template).path("targetDate").isNull()).isTrue();
        send(owner,"/api/v1/lists/"+template+"/instantiate",Map.of("title","No date"),400);
        var fresh=send(owner,"/api/v1/lists/"+template+"/instantiate",Map.of("title","Next party","targetDate","2027-04-05T12:00:00Z"),201);
        assertThat(fresh.path("targetDate").asText()).isEqualTo("2027-04-05T12:00:00Z");
        var copied=read(owner,"/api/v1/lists/"+id(fresh)+"/items").get(0);
        assertThat(copied.path("status").asText()).isEqualTo("OPEN");assertThat(copied.path("reservedByGuest").isNull()).isTrue();
    }

    @Test void deletedItemsCannotBeClaimedThroughStillActivePublicLinks() throws Exception {
        var owner=user("owner");var list=id(list(owner,"Wishlist","WISH"));var item=id(item(owner,list,"Gift",null));
        var token=send(owner,"/api/v1/lists/"+list+"/public-share",Map.of("mode","WISH_CLAIM"),201).path("shareToken").asText();
        mvc.perform(delete("/api/v1/items/"+item).session(owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/share/"+token)).andExpect(status().isOk()).andExpect(jsonPath("$.items",hasSize(0)));
        mvc.perform(post("/api/v1/share/"+token+"/items/"+item+"/claim").contentType("application/json").content("{\"guestName\":\"Guest\"}"))
            .andExpect(status().isNotFound());
        assertThat(items.findById(item).orElseThrow().getStatus()).isEqualTo(ItemStatus.OPEN);
    }

    @Test void staleConcurrentListWriteCannotReactivateArchivedSharing() throws Exception {
        var owner=user("owner");var id=id(list(owner,"Private list","TODO"));
        var first=lists.findById(id).orElseThrow();var stale=lists.findById(id).orElseThrow();
        first.setArchived(true);lists.saveAndFlush(first);
        stale.enablePublicShare("must-not-be-persisted");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> lists.saveAndFlush(stale))
            .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
        var current=lists.findById(id).orElseThrow();assertThat(current.isArchived()).isTrue();assertThat(current.isPublicList()).isFalse();
    }

    @Test void clearingCompletedGroceriesUsesRecoverableTrash() throws Exception {
        var owner=user("owner");var list=id(list(owner,"Groceries","GROCERY"));
        var bought=send(owner,"/api/v1/lists/"+list+"/items",Map.of("name","Milk","status","DONE","quantity","2 cartons"),201);
        item(owner,list,"Bread",null);
        mvc.perform(delete("/api/v1/lists/"+list+"/items/completed").session(owner)).andExpect(status().isNoContent());
        assertThat(read(owner,"/api/v1/lists/"+list+"/items").size()).isEqualTo(1);
        assertThat(read(owner,"/api/v1/trash/items").size()).isEqualTo(1);
        mvc.perform(post("/api/v1/items/"+id(bought)+"/restore").session(owner)).andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value("2 cartons"));
        assertThat(read(owner,"/api/v1/lists/"+list+"/items").size()).isEqualTo(2);
    }
}
