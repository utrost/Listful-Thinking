package app.listful.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/health"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/health", "/", "/index.html", "/assets/**", "/favicon.ico").permitAll()
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
