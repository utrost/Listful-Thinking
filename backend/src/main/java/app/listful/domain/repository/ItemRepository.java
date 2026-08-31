package app.listful.domain.repository;

import app.listful.domain.Item;
import app.listful.domain.enums.ItemStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, String> {
    List<Item> findByListId(String listId);

    @Query("""
        select i from Item i
        join fetch i.list l
        join fetch l.user u
        where i.dueDate is not null
          and i.dueDate >= :start
          and i.dueDate < :end
          and i.status <> :excludedStatus
        """)
    List<Item> findDueItemsBetween(
        @Param("start") Instant start,
        @Param("end") Instant end,
        @Param("excludedStatus") ItemStatus excludedStatus
    );
}
