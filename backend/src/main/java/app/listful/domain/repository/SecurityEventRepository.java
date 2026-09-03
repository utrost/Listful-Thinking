package app.listful.domain.repository;

import app.listful.domain.SecurityEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, String> {
    List<SecurityEvent> findByType(String type);
}
