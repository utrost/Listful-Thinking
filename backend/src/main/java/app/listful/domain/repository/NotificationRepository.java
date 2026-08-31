package app.listful.domain.repository;

import app.listful.domain.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByUserIdAndReadAtIsNull(String userId);
}
