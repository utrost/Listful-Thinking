package app.listful.planning;

import app.listful.domain.RetentionPolicy;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionService {
    private static final Logger logger = LoggerFactory.getLogger(RetentionService.class);
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RetentionService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${listful.retention.interval-ms:86400000}",
        initialDelayString = "${listful.retention.initial-delay-ms:60000}")
    public void purgeExpired() {
        purgeExpired(clock.instant());
    }

    @Transactional
    public void purgeExpired(Instant now) {
        String cutoff = now.minus(RetentionPolicy.TRASH).toString();
        // SQLite stores Instant fields as ISO text. julianday handles both whole
        // seconds and fractional seconds at the exact retention boundary.
        int items = jdbc.update("DELETE FROM items WHERE deleted_at IS NOT NULL AND julianday(deleted_at) <= julianday(?)", cutoff);
        // Foreign keys cascade to every remaining item and registered-user share.
        int lists = jdbc.update("DELETE FROM lists WHERE deleted_at IS NOT NULL AND julianday(deleted_at) <= julianday(?)", cutoff);
        int links = jdbc.update("""
            UPDATE lists SET share_token = NULL, is_public = 0, public_share_expires_at = NULL, version = version + 1
            WHERE is_public = 1 AND (public_share_expires_at IS NULL OR julianday(public_share_expires_at) <= julianday(?))
            """, now.toString());
        if (items + lists + links > 0) {
            logger.info("Retention cleanup removed {} item rows directly, {} trashed lists (with their remaining items), and {} expired public links", items, lists, links);
        }
    }
}
