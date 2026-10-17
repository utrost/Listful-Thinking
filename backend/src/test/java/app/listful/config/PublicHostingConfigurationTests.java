package app.listful.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PublicHostingConfigurationTests {
    private final SecurityHardeningProperties security = new SecurityHardeningProperties();

    @Test
    void publicModeRequiresHttpsAndSecureCookie() {
        assertThatThrownBy(() -> new PublicHostingConfiguration(security, true, "http://lists.example.org", true))
            .hasMessageContaining("PUBLIC_BASE_URL");
        assertThatThrownBy(() -> new PublicHostingConfiguration(security, true, "https://lists.example.org", false))
            .hasMessageContaining("SESSION_COOKIE_SECURE");
    }

    @Test
    void publicModeRejectsDisabledGuards() {
        security.setCsrfEnabled(false);
        assertThatThrownBy(() -> new PublicHostingConfiguration(security, true, "https://lists.example.org", true))
            .hasMessageContaining("CSRF_ENABLED");
        security.setCsrfEnabled(true);
        security.setRateLimitEnabled(false);
        assertThatThrownBy(() -> new PublicHostingConfiguration(security, true, "https://lists.example.org", true))
            .hasMessageContaining("RATE_LIMIT_ENABLED");
        security.setRateLimitEnabled(true);
        security.setScraperAllowPrivateAddresses(true);
        assertThatThrownBy(() -> new PublicHostingConfiguration(security, true, "https://lists.example.org", true))
            .hasMessageContaining("SCRAPER_ALLOW_PRIVATE_ADDRESSES");
    }
}
