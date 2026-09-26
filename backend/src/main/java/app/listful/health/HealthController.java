package app.listful.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> database;

    public HealthController(org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> database) {
        this.database = database;
    }

    @GetMapping("/ready")
    public org.springframework.http.ResponseEntity<Map<String, String>> ready() {
        try {
            var jdbc = database.getIfAvailable();
            if (jdbc == null) throw new IllegalStateException("Database unavailable");
            jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
            return org.springframework.http.ResponseEntity.ok(Map.of("status", "ok"));
        } catch (RuntimeException ex) {
            return org.springframework.http.ResponseEntity.status(503).body(Map.of("status", "unavailable"));
        }
    }

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
