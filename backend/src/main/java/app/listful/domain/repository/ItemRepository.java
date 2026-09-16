package app.listful.domain.repository;

import app.listful.domain.Item;
import app.listful.domain.enums.ItemStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, String> {
    List<Item> findByListId(String listId);
    long deleteByListIdAndStatus(String listId, ItemStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Item i
        set i.status = :claimedStatus,
            i.reservedByGuest = :guestName
        where i.id = :itemId
          and i.list.id = :listId
          and i.status = :openStatus
        """)
    int claimOpenItem(
        @Param("itemId") String itemId,
        @Param("listId") String listId,
        @Param("guestName") String guestName,
        @Param("openStatus") ItemStatus openStatus,
        @Param("claimedStatus") ItemStatus claimedStatus
    );

    @Query("""
        select i from Item i
        join fetch i.list l
        join fetch l.user u
        where i.dueDate is not null
          and i.dueDate >= :start
          and i.dueDate < :end
          and i.status not in :excludedStatuses
        """)
    List<Item> findDueItemsBetween(
        @Param("start") Instant start,
        @Param("end") Instant end,
        @Param("excludedStatuses") List<ItemStatus> excludedStatuses
    );
}
