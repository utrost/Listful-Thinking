package app.listful.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LocationMigrationTests {
    @TempDir Path directory;

    @Test
    void upgradePreservesTripAndSharesAndAllowsLocations() {
        var datasource = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("locations.sqlite"));
        Flyway.configure().dataSource(datasource).mixed(true)
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("19"))
            .load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("insert into users (id, username, password_hash, role, created_at) values ('owner', 'owner', 'hash', 'USER', '2026-10-24T10:00:00Z')");
        jdbc.update("insert into users (id, username, password_hash, role, created_at) values ('friend', 'friend', 'hash', 'USER', '2026-10-24T10:00:00Z')");
        jdbc.update("insert into lists (id, user_id, title, type, created_at) values ('trip', 'owner', 'Berlin', 'TRIP', '2026-10-24T10:00:00Z')");
        jdbc.update("insert into items (id, list_id, name, status, suggested_by_name) values ('place', 'trip', 'Museum Island', 'OPEN', 'friend')");
        jdbc.update("insert into list_shares (list_id, user_id, created_at, permission) values ('trip', 'friend', '2026-10-24T10:00:00Z', 'CONTRIBUTE')");

        Flyway.configure().dataSource(datasource).mixed(true).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("select name from items where id='place'", String.class)).isEqualTo("Museum Island");
        assertThat(jdbc.queryForObject("select suggested_by_name from items where id='place'", String.class)).isEqualTo("friend");
        assertThat(jdbc.queryForObject("select permission from list_shares where list_id='trip'", String.class)).isEqualTo("CONTRIBUTE");
        jdbc.update("insert into lists (id, user_id, title, type, created_at) values ('favorites', 'owner', 'Favorites', 'LOCATION', '2026-10-24T10:00:00Z')");
        jdbc.update("update items set address='Bodestraße 1, Berlin', latitude=52.5201, longitude=13.3976 where id='place'");
        assertThat(jdbc.queryForObject("select type from lists where id='favorites'", String.class)).isEqualTo("LOCATION");
        assertThat(jdbc.queryForObject("select latitude from items where id='place'", Double.class)).isEqualTo(52.5201);
        assertThat(jdbc.queryForList("pragma foreign_key_check")).isEmpty();
    }
}
