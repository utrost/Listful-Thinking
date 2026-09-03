package app.listful.config;

import app.listful.api.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHardeningFilter extends OncePerRequestFilter {
    private final SecurityHardeningProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public SecurityHardeningFilter(SecurityHardeningProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest boundedRequest = request;
        BodyLimitResult bodyLimitResult = enforceBodyLimit(request);
        if (bodyLimitResult.tooLarge()) {
            writeError(response, HttpStatus.PAYLOAD_TOO_LARGE, new ApiError("payload_too_large", "Request body is too large."));
            return;
        }
        if (bodyLimitResult.request() != null) {
            boundedRequest = bodyLimitResult.request();
        }

        if (isRateLimited(boundedRequest)) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, new ApiError("rate_limited", "Too many requests. Please retry later."));
            return;
        }
        filterChain.doFilter(boundedRequest, response);
    }

    private BodyLimitResult enforceBodyLimit(HttpServletRequest request) throws IOException {
        long maxBytes = properties.getMaxRequestBodyBytes();
        if (maxBytes <= 0 || !request.getRequestURI().startsWith("/api/v1/") || !methodCanHaveBody(request)) {
            return BodyLimitResult.allowed(null);
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            return BodyLimitResult.rejected();
        }

        int readLimit = (int) Math.min(maxBytes + 1, Integer.MAX_VALUE);
        byte[] body = request.getInputStream().readNBytes(readLimit);
        if (body.length > maxBytes) {
            return BodyLimitResult.rejected();
        }
        return BodyLimitResult.allowed(new CachedBodyRequest(request, body));
    }

    private boolean methodCanHaveBody(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
    }

    private boolean isRateLimited(HttpServletRequest request) {
        if (!properties.isRateLimitEnabled() || properties.getRateLimitMaxRequests() <= 0 || !isSensitiveEndpoint(request)) {
            return false;
        }
        long windowMillis = Math.max(1, properties.getRateLimitWindowSeconds()) * 1000;
        long windowStartedAt = (System.currentTimeMillis() / windowMillis) * windowMillis;
        cleanupOldCounters(windowStartedAt, windowMillis);
        if (counters.size() >= properties.getRateLimitMaxBuckets()) {
            return true;
        }

        String key = clientIp(request) + " " + request.getMethod() + " " + request.getRequestURI() + " " + windowStartedAt;
        WindowCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStartedAt != windowStartedAt) {
                return new WindowCounter(windowStartedAt);
            }
            existing.requests.incrementAndGet();
            return existing;
        });
        return counter.requests.get() > properties.getRateLimitMaxRequests();
    }

    private boolean isSensitiveEndpoint(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/login")
            || path.equals("/api/v1/auth/register")
            || path.equals("/api/v1/auth/magic-link")
            || path.equals("/api/v1/auth/magic-link/consume")
            || path.equals("/api/v1/auth/password-reset")
            || path.equals("/api/v1/auth/password-reset/consume")
            || path.equals("/api/v1/utils/scrape")
            || (path.startsWith("/api/v1/share/") && path.contains("/items/") && path.endsWith("/claim"));
    }

    private String clientIp(HttpServletRequest request) {
        if (properties.isTrustForwardedFor()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",", 2)[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void cleanupOldCounters(long currentWindowStartedAt, long windowMillis) {
        counters.entrySet().removeIf(entry -> entry.getValue().windowStartedAt < currentWindowStartedAt - windowMillis);
    }

    private void writeError(HttpServletResponse response, HttpStatus status, ApiError error) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), error);
    }

    private record BodyLimitResult(boolean tooLarge, HttpServletRequest request) {
        private static BodyLimitResult rejected() {
            return new BodyLimitResult(true, null);
        }

        private static BodyLimitResult allowed(HttpServletRequest request) {
            return new BodyLimitResult(false, request);
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream source;

        private CachedBodyServletInputStream(byte[] body) {
            this.source = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return source.read();
        }

        @Override
        public boolean isFinished() {
            return source.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Async request body reads are not supported.");
        }
    }

    private static final class WindowCounter {
        private final long windowStartedAt;
        private final AtomicInteger requests = new AtomicInteger(1);

        private WindowCounter(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}
