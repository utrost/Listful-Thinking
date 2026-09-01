package app.listful.domain.repository;

import app.listful.domain.ListEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListRepository extends JpaRepository<ListEntity, String> {
    List<ListEntity> findByUserId(String userId);
    @Query("""
        select distinct l from ListEntity l
        where l.user.id = :userId
           or l.id in (select s.list.id from ListShare s where s.user.id = :userId)
        order by l.createdAt desc
        """)
    List<ListEntity> findAccessibleByUserId(@Param("userId") String userId);
    Optional<ListEntity> findByShareToken(String shareToken);
}
