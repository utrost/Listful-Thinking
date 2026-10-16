package app.listful.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RetentionMigrationTests {
    @TempDir Path directory;

    @Test
    void upgradeGivesExistingLinksEightWeeksFromMigration() {
        var datasource = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("upgrade.sqlite"));
        Flyway.configure().dataSource(datasource).mixed(true)
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion("16"))
            .load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("insert into users (id, username, password_hash, role, created_at) values ('owner', 'owner', 'hash', 'USER', ?)", Instant.now().toString());
        jdbc.update("""
            insert into lists (id, user_id, title, type, created_at, is_public, share_token)
            values ('shared', 'owner', 'Shared', 'WISH', ?, 1, 'sha256:legacy')
            """, Instant.now().toString());

        Instant before = Instant.now();
        Flyway.configure().dataSource(datasource).mixed(true)
            .locations("classpath:db/migration").load().migrate();
        String expiry = jdbc.queryForObject("select public_share_expires_at from lists where id='shared'", String.class);

        assertThat(expiry).isNotNull();
        assertThat(Duration.between(before, Instant.parse(expiry)))
            .isBetween(Duration.ofDays(56).minusSeconds(1), Duration.ofDays(56).plusSeconds(1));
    }
}
