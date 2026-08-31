package app.listful.domain.repository;

import app.listful.domain.ListEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListRepository extends JpaRepository<ListEntity, String> {
    List<ListEntity> findByUserId(String userId);
    Optional<ListEntity> findByShareToken(String shareToken);
}
