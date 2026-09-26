package app.listful.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import app.listful.config.SecurityConfig;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.jdbc.core.JdbcTemplate database;

    @Test
    void readinessReportsDatabaseFailures() throws Exception {
        org.mockito.Mockito.when(database.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        mockMvc.perform(get("/api/v1/health/ready")).andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }
}
