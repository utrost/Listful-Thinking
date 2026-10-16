package app.listful.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:file:retention-service-test?mode=memory&cache=shared"
})
class RetentionServiceTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired RetentionService retention;
    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    @BeforeEach
    void reset() {
        jdbc.update("delete from list_shares");
        jdbc.update("delete from items");
        jdbc.update("delete from lists");
        jdbc.update("delete from users");
    }

    @Test
    void purgesOnlyRecordsAtOrBeyondTwoWeeksAndExpiresPublicLinks() {
        user("owner");
        user("reader");
        list("old-list", NOW.minusSeconds(15L * 86400).toString(), null, null);
        list("boundary-list", NOW.minusSeconds(14L * 86400).toString(), null, null);
        list("young-list", NOW.minusSeconds(13L * 86400).toString(), null, null);
        list("active-list", null, "sha256:expired", NOW.toString());
        list("future-link", null, "sha256:future", NOW.plusSeconds(1).toString());
        item("in-old-list", "old-list", null);
        item("old-item", "young-list", NOW.minusSeconds(15L * 86400).toString());
        item("boundary-item", "young-list", NOW.minusSeconds(14L * 86400).toString());
        item("young-item", "young-list", NOW.minusSeconds(13L * 86400).toString());
        jdbc.update("insert into list_shares (list_id, user_id, created_at) values (?, ?, ?)", "old-list", "reader", NOW.toString());

        retention.purgeExpired(NOW);

        assertThat(ids("lists")).containsExactlyInAnyOrder("young-list", "active-list", "future-link");
        assertThat(ids("items")).containsExactly("young-item");
        assertThat(jdbc.queryForObject("select count(*) from list_shares", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select is_public from lists where id='active-list'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select share_token from lists where id='active-list'", String.class)).isNull();
        assertThat(jdbc.queryForObject("select is_public from lists where id='future-link'", Integer.class)).isEqualTo(1);
        retention.purgeExpired(NOW);
        assertThat(ids("items")).containsExactly("young-item");
    }

    private void user(String id) {
        jdbc.update("insert into users (id, username, password_hash, role, created_at) values (?, ?, 'hash', 'USER', ?)", id, id, NOW.toString());
    }

    private void list(String id, String deletedAt, String token, String expiresAt) {
        jdbc.update("""
            insert into lists (id, user_id, title, type, created_at, deleted_at, share_token, is_public, public_share_expires_at)
            values (?, 'owner', ?, 'TODO', ?, ?, ?, ?, ?)
            """, id, id, NOW.toString(), deletedAt, token, token == null ? 0 : 1, expiresAt);
    }

    private void item(String id, String listId, String deletedAt) {
        jdbc.update("insert into items (id, list_id, name, status, deleted_at) values (?, ?, ?, 'OPEN', ?)", id, listId, id, deletedAt);
    }

    private java.util.List<String> ids(String table) {
        return jdbc.queryForList("select id from " + table, String.class);
    }
}
