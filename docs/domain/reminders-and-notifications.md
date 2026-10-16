# Reminders and Notifications

The current implementation is described in more detail in [Reminders](reminders.md). This page covers the user-visible delivery and notification model.

## Due-item reminders

The scheduler runs about once per minute, starting one minute after startup. It scans unfinished TODO, CHORE, and EVENT items due within the next 24 hours, including overdue work missed during downtime. Event target dates alone do not trigger reminders; WISH and GROCERY items are excluded. A recurring chore gets a new reminder occurrence when its due timestamp advances.

A unique delivery key combines the item ID and due timestamp. The server persists an unread inbox notification first. If mail delivery succeeds, it marks that notification read; if mail is unavailable or fails, the unread notification remains. Automatic scans do not repeatedly email the same occurrence. SMTP and SQLite are separate systems, so there is no atomic email/database transaction or automatic mail retry queue.

## Sharing and ownership notifications

An active user receives an in-app notification when a list is shared with them, their permission changes, or their access is revoked. Owner transfer notifies both the previous and new owner. Admin transfer from an inactive owner also records notifications, although the inactive owner cannot read theirs until reactivated. These events are separate from due-item reminder delivery keys.

## Delivery and localization

Username/password access and the in-app inbox work without SMTP. Mail delivery uses the configured Spring mail host; an authenticated provider also needs its matching port, credentials, and TLS settings. See the [self-hosting handbook](../deployment/self-hosting-handbook.md). Notifications store a message key, compact arguments, creation time, and optional read time; the UI localizes them when displayed. Due-item notifications additionally store the unique delivery key.

## Limits

Large installations may need scan pagination, user-level time zones, a durable outbound-mail queue, and separate notification retention. The current target is a small private instance; backup retention is an operator decision.
