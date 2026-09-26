package app.listful.planning;

import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.lists.CurrentUser;
import app.listful.lists.ListAccessService;
import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PlanningController {
    private final ItemRepository items;
    private final ListRepository lists;
    private final ListAccessService access;
    private final Clock clock;
    public PlanningController(ItemRepository items, ListRepository lists, ListAccessService access, Clock clock) {
        this.items = items; this.lists = lists; this.access = access; this.clock = clock;
    }
    public record Entry(String kind, String id, String listId, String listTitle, String listType,
        String name, String dueDate, String access, long version, String recurrenceRule, String ownerLabel) {}
    public record Overview(String date, String zone, List<Entry> overdue, List<Entry> today, List<Entry> upcoming) {}
    public record TrashedItem(String id, String listId, String listTitle, String name, String deletedAt, boolean listArchived) {}

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Overview overview(@RequestParam(defaultValue = "UTC") String zone, Authentication authentication) {
        ZoneId timezone;
        try { timezone = ZoneId.of(zone); }
        catch (DateTimeException ex) { throw new app.listful.api.ValidationFailedException("Choose a valid timezone."); }
        var actor = CurrentUser.from(authentication);
        LocalDate date = LocalDate.now(clock.withZone(timezone));
        Instant start = date.atStartOfDay(timezone).toInstant();
        Instant tomorrow = date.plusDays(1).atStartOfDay(timezone).toInstant();
        Instant end = date.plusDays(8).atStartOfDay(timezone).toInstant();
        List<Entry> entries = new ArrayList<>();
        for (var item : items.findOverviewItems(actor.getId(), end)) {
            var list = item.getList();
            entries.add(new Entry("ITEM", item.getId(), list.getId(), list.getTitle(), list.getType().name(),
                item.getName(), item.getDueDate().toString(), access.accessMode(actor, list), item.getVersion(), item.getRecurrenceRule(), item.getOwnerLabel()));
        }
        for (var list : lists.findAccessibleByUserId(actor.getId())) {
            if (list.getTargetDate() != null && list.getTargetDate().isBefore(end)) {
                entries.add(new Entry("EVENT", list.getId(), list.getId(), list.getTitle(), list.getType().name(),
                    list.getTitle(), list.getTargetDate().toString(), access.accessMode(actor, list), 0, null, null));
            }
        }
        entries.sort(Comparator.comparing((Entry entry) -> Instant.parse(entry.dueDate())).thenComparing(Entry::listTitle).thenComparing(Entry::name).thenComparing(Entry::id));
        return new Overview(date.toString(), timezone.getId(),
            entries.stream().filter(e -> Instant.parse(e.dueDate()).isBefore(start)).toList(),
            entries.stream().filter(e -> !Instant.parse(e.dueDate()).isBefore(start) && Instant.parse(e.dueDate()).isBefore(tomorrow)).toList(),
            entries.stream().filter(e -> !Instant.parse(e.dueDate()).isBefore(tomorrow)).toList());
    }

    @GetMapping("/trash/items")
    @Transactional(readOnly = true)
    public List<TrashedItem> trash(Authentication authentication) {
        var actor = CurrentUser.from(authentication);
        return items.findDeletedOwnedItems(actor.getId()).stream().map(item -> new TrashedItem(item.getId(),
            item.getList().getId(), item.getList().getTitle(), item.getName(), item.getDeletedAt().toString(), item.getList().isArchived())).toList();
    }
}
