package app.listful.domain.repository;

import app.listful.domain.ListShare;
import app.listful.domain.ListShareId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListShareRepository extends JpaRepository<ListShare, ListShareId> {
    List<ListShare> findByUserId(String userId);
    List<ListShare> findByListId(String listId);
    Optional<ListShare> findByListIdAndUserId(String listId, String userId);
    boolean existsByListIdAndUserId(String listId, String userId);
}
