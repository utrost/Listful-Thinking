package app.listful;

import app.listful.config.SecurityHardeningProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(SecurityHardeningProperties.class)
public class ListfulThinkingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ListfulThinkingApplication.class, args);
    }
}
