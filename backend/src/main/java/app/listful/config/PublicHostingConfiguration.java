package app.listful.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PublicHostingConfiguration {
    public PublicHostingConfiguration(
        SecurityHardeningProperties security,
        @Value("${listful.public-hosting:false}") boolean publicHosting,
        @Value("${listful.public-base-url:http://localhost:8080}") String publicBaseUrl,
        @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie
    ) {
        if (!publicHosting) return;
        URI url;
        try {
            url = URI.create(publicBaseUrl);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("PUBLIC_BASE_URL must be a valid HTTPS origin for public hosting", ex);
        }
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null
            || url.getRawUserInfo() != null || url.getRawQuery() != null || url.getRawFragment() != null
            || (url.getRawPath() != null && !url.getRawPath().isBlank() && !"/".equals(url.getRawPath()))) {
            throw new IllegalStateException("PUBLIC_BASE_URL must be a valid HTTPS origin for public hosting");
        }
        if (!secureCookie) throw new IllegalStateException("SESSION_COOKIE_SECURE must be true for public hosting");
        if (!security.isCsrfEnabled()) throw new IllegalStateException("CSRF_ENABLED must be true for public hosting");
        if (!security.isRateLimitEnabled() || security.getRateLimitMaxRequests() <= 0
            || security.getRateLimitWindowSeconds() <= 0 || security.getRateLimitMaxBuckets() <= 0)
            throw new IllegalStateException("RATE_LIMIT_ENABLED must be true for public hosting");
        if (security.isScraperAllowPrivateAddresses())
            throw new IllegalStateException("SCRAPER_ALLOW_PRIVATE_ADDRESSES must be false for public hosting");
    }
}
