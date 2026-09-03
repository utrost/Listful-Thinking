package app.listful.security;

import app.listful.domain.SecurityEvent;
import app.listful.domain.repository.SecurityEventRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditService {
    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditService.class);
    private final SecurityEventRepository repository;

    public SecurityAuditService(SecurityEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String type, String actorId, String clientIp, String path, String details) {
        String safeDetails = details == null ? null : details.substring(0, Math.min(details.length(), 2000));
        try {
            repository.save(new SecurityEvent(type, actorId, clientIp, path, safeDetails, Instant.now()));
        } catch (RuntimeException ex) {
            logger.warn("Failed to persist security event {}: {}", type, ex.getMessage());
        }
        logger.info("security_event type={} actorId={} clientIp={} path={}", type, actorId, clientIp, path);
    }
}
