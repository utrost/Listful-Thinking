package app.listful.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:file:schema-migration-test?mode=memory&cache=shared"
})
class SchemaMigrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesAllMvpTables() {
        List<String> tables = jdbcTemplate.queryForList(
            "select name from sqlite_master where type = 'table'",
            String.class
        );

        assertThat(tables).contains(
            "users",
            "lists",
            "items",
            "settings",
            "list_shares",
            "notifications"
        );
    }

    @Test
    void flywayCreatesAuthorizationAndReminderIndexes() {
        List<String> indexes = jdbcTemplate.queryForList(
            "select name from sqlite_master where type = 'index'",
            String.class
        );

        assertThat(indexes).contains(
            "idx_lists_user_id",
            "idx_items_list_id",
            "idx_lists_share_token",
            "idx_notifications_user_id_read_at",
            "idx_list_shares_user_id"
        );
    }

    @Test
    void flywayCreatesItemResponsibilityColumns() {
        List<String> columns = jdbcTemplate.queryForList(
            "select name from pragma_table_info('items')",
            String.class
        );

        assertThat(columns).contains("owner_label", "assistant_labels");
    }

}
