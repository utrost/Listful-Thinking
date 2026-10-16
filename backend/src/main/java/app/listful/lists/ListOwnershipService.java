package app.listful.lists;

import app.listful.api.ConflictException;
import app.listful.api.ResourceNotFoundException;
import app.listful.domain.ListEntity;
import app.listful.domain.Notification;
import app.listful.domain.User;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.ListShareRepository;
import app.listful.domain.repository.NotificationRepository;
import app.listful.domain.repository.UserRepository;
import app.listful.security.SecurityAuditService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListOwnershipService {
    private final ListAccessService access;
    private final ListRepository lists;
    private final ListShareRepository shares;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final SecurityAuditService audit;

    public ListOwnershipService(ListAccessService access, ListRepository lists, ListShareRepository shares,
        UserRepository users, NotificationRepository notifications, SecurityAuditService audit) {
        this.access = access;
        this.lists = lists;
        this.shares = shares;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional
    public void transferOwnedList(User actor, String listId, String username, String ip, String path) {
        ListEntity list = access.requireOwnedList(actor, listId);
        User target = requireActiveTarget(username, actor);
        transfer(list, target, actor);
        audit.recordRequired("list_ownership_transferred", actor.getId(), ip, path,
            "listId=%s fromUserId=%s toUserId=%s".formatted(listId, actor.getId(), target.getId()));
    }

    @Transactional
    public int transferInactiveUserLists(User admin, String sourceId, String username, String ip, String path) {
        User source = users.findById(sourceId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (source.isActive()) throw new ConflictException("owner_active", "Deactivate the owner before an admin transfer.");
        User target = requireActiveTarget(username, source);
        if (target.getId().equals(admin.getId())) throw new ConflictException("self_transfer", "Admins cannot transfer another user's lists to themselves.");
        List<ListEntity> owned = lists.findByUserId(sourceId);
        if (owned.isEmpty()) throw new ConflictException("no_lists_to_transfer", "This account owns no lists.");
        for (ListEntity list : owned) transfer(list, target, source);
        audit.recordRequired("admin_list_ownership_transferred", admin.getId(), ip, path,
            "fromUserId=%s toUserId=%s listCount=%d".formatted(sourceId, target.getId(), owned.size()));
        return owned.size();
    }

    private User requireActiveTarget(String username, User source) {
        User target = users.findByUsername(username.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!target.isActive()) throw new ConflictException("inactive_user", "The new owner must have an active account.");
        if (target.getId().equals(source.getId())) throw new ConflictException("same_owner", "Choose another account as the new owner.");
        return target;
    }

    private void transfer(ListEntity list, User target, User previousOwner) {
        shares.findByListIdAndUserId(list.getId(), target.getId()).ifPresent(shares::delete);
        list.disablePublicShare();
        list.setUser(target);
        lists.save(list);
        String args = list.getId() + "|" + list.getTitle().replace('|', ' ') + "|" + previousOwner.getUsername().replace('|', ' ');
        notifications.save(new Notification(target, "notification.list_ownership_received", args, Instant.now()));
        notifications.save(new Notification(previousOwner, "notification.list_ownership_sent",
            list.getId() + "|" + list.getTitle().replace('|', ' ') + "|" + target.getUsername().replace('|', ' '), Instant.now()));
    }
}
