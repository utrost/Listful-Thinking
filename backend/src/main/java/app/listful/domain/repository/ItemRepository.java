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
    @Query("select i from Item i where i.list.id = :listId and i.deletedAt is null")
    List<Item> findByListId(@Param("listId") String listId);
    boolean existsByListId(String listId);
    @Query("select i from Item i where i.importStatus = :importStatus and i.deletedAt is null and i.list.deletedAt is null and i.list.archived = 0 and i.list.templateFlag = 0")
    List<Item> findByImportStatus(@Param("importStatus") String importStatus);
    @Query("select i from Item i join fetch i.list l where l.user.id = :userId and i.deletedAt is not null and l.deletedAt is null order by i.deletedAt desc")
    List<Item> findDeletedOwnedItems(@Param("userId") String userId);
    @Query("""
        select i from Item i join fetch i.list l where i.deletedAt is null and l.deletedAt is null
        and l.archived = 0 and l.templateFlag = 0 and i.status = app.listful.domain.enums.ItemStatus.OPEN
        and i.dueDate < :end and (l.user.id = :userId or l.id in (select s.list.id from ListShare s where s.user.id = :userId))
        order by i.dueDate, l.title, i.name, i.id
        """)
    List<Item> findOverviewItems(@Param("userId") String userId, @Param("end") Instant end);
    long deleteByListIdAndStatus(String listId, ItemStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Item i
        set i.status = :claimedStatus,
            i.version = i.version + 1,
            i.reservedByGuest = :guestName
        where i.id = :itemId
          and i.list.id = :listId
          and i.status = :openStatus and i.deletedAt is null
          and i.list.id in (select l.id from ListEntity l where l.deletedAt is null and l.archived = 0 and l.templateFlag = 0)
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
        where i.deletedAt is null and l.deletedAt is null and l.archived = 0 and l.templateFlag = 0 and i.dueDate is not null
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
