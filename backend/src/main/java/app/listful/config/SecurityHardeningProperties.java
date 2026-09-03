package app.listful.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "listful.security")
public class SecurityHardeningProperties {
    private boolean rateLimitEnabled = true;
    private int rateLimitMaxRequests = 60;
    private long rateLimitWindowSeconds = 60;
    private int rateLimitMaxBuckets = 10_000;
    private boolean trustForwardedFor = false;
    private long maxRequestBodyBytes = 65_536;
    private boolean csrfEnabled = true;
    private boolean scraperAllowPrivateAddresses = false;

    public boolean isRateLimitEnabled() { return rateLimitEnabled; }
    public void setRateLimitEnabled(boolean rateLimitEnabled) { this.rateLimitEnabled = rateLimitEnabled; }
    public int getRateLimitMaxRequests() { return rateLimitMaxRequests; }
    public void setRateLimitMaxRequests(int rateLimitMaxRequests) { this.rateLimitMaxRequests = rateLimitMaxRequests; }
    public long getRateLimitWindowSeconds() { return rateLimitWindowSeconds; }
    public void setRateLimitWindowSeconds(long rateLimitWindowSeconds) { this.rateLimitWindowSeconds = rateLimitWindowSeconds; }
    public int getRateLimitMaxBuckets() { return rateLimitMaxBuckets; }
    public void setRateLimitMaxBuckets(int rateLimitMaxBuckets) { this.rateLimitMaxBuckets = rateLimitMaxBuckets; }
    public boolean isTrustForwardedFor() { return trustForwardedFor; }
    public void setTrustForwardedFor(boolean trustForwardedFor) { this.trustForwardedFor = trustForwardedFor; }
    public long getMaxRequestBodyBytes() { return maxRequestBodyBytes; }
    public void setMaxRequestBodyBytes(long maxRequestBodyBytes) { this.maxRequestBodyBytes = maxRequestBodyBytes; }
    public boolean isCsrfEnabled() { return csrfEnabled; }
    public void setCsrfEnabled(boolean csrfEnabled) { this.csrfEnabled = csrfEnabled; }
    public boolean isScraperAllowPrivateAddresses() { return scraperAllowPrivateAddresses; }
    public void setScraperAllowPrivateAddresses(boolean scraperAllowPrivateAddresses) { this.scraperAllowPrivateAddresses = scraperAllowPrivateAddresses; }
}
