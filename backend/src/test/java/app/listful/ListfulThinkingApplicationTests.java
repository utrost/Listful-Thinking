package app.listful;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:file:listful-test?mode=memory&cache=shared"
})
class ListfulThinkingApplicationTests {
    @Test
    void contextLoads() {
    }
}
