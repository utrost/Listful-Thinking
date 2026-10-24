package app.listful.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TripMigrationTests {
    @TempDir Path directory;

    @Test
    void upgradePreservesListsItemsAndContributorShares() {
        var datasource = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("trip-upgrade.sqlite"));
        Flyway.configure().dataSource(datasource).mixed(true)
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("18"))
            .load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("insert into users (id, username, password_hash, role, created_at) values ('owner', 'owner', 'hash', 'USER', '2026-10-24T10:00:00Z')");
        jdbc.update("insert into users (id, username, password_hash, role, created_at) values ('friend', 'friend', 'hash', 'USER', '2026-10-24T10:00:00Z')");
        jdbc.update("insert into lists (id, user_id, title, type, created_at, source_url, version) values ('old', 'owner', 'Existing list', 'WISH', '2026-10-24T10:00:00Z', 'https://example.test', 2)");
        jdbc.update("insert into items (id, list_id, name, status) values ('item', 'old', 'Existing item', 'OPEN')");
        jdbc.update("insert into list_shares (list_id, user_id, created_at, permission) values ('old', 'friend', '2026-10-24T10:00:00Z', 'CONTRIBUTE')");

        Flyway.configure().dataSource(datasource).mixed(true)
            .locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("select title from lists where id='old'", String.class)).isEqualTo("Existing list");
        assertThat(jdbc.queryForObject("select source_url from lists where id='old'", String.class)).isEqualTo("https://example.test");
        assertThat(jdbc.queryForObject("select version from lists where id='old'", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select name from items where id='item'", String.class)).isEqualTo("Existing item");
        assertThat(jdbc.queryForObject("select permission from list_shares where list_id='old'", String.class)).isEqualTo("CONTRIBUTE");
        assertThat(jdbc.queryForList("pragma foreign_key_check")).isEmpty();
        jdbc.update("insert into lists (id, user_id, title, type, created_at) values ('trip', 'owner', 'Berlin', 'TRIP', '2026-10-24T10:00:00Z')");
        assertThat(jdbc.queryForObject("select type from lists where id='trip'", String.class)).isEqualTo("TRIP");
    }
}
