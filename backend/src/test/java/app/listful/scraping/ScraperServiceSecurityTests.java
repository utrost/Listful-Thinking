package app.listful.scraping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.listful.api.ValidationFailedException;
import app.listful.config.SecurityHardeningProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ScraperServiceSecurityTests {
    private final SecurityHardeningProperties properties = new SecurityHardeningProperties();
    private final ScraperService scraperService = new ScraperService(new ObjectMapper(), properties);

    @Test
    void rejectsIpv6UniqueLocalAddresses() {
        assertThatThrownBy(() -> scraperService.scrape("http://[fc00::1]/metadata"))
            .isInstanceOf(ValidationFailedException.class)
            .hasMessageContaining("private");
    }

    @Test
    void validatesRedirectTargetsBeforeFollowingThem() throws Exception {
        assertThatThrownBy(() -> scraperService.validateRedirectLocation(
                new URI("https://shop.example/products/pen"),
                "http://127.0.0.1:8080/internal"))
            .isInstanceOf(ValidationFailedException.class)
            .hasMessageContaining("private");
    }

    @Test
    void allowsSafeRelativeRedirectTargets() throws Exception {
        URI next = scraperService.validateRedirectLocation(
            new URI("http://93.184.216.34/products/pen"),
            "/products/fountain-pen");

        assertThat(next).isEqualTo(new URI("http://93.184.216.34/products/fountain-pen"));
    }
}
