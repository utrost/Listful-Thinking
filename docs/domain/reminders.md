# Reminders

The scheduler scans once per minute, starting one minute after application startup. It considers unfinished items due before the next 24 hours, including overdue items missed during downtime. Times are stored as UTC instants; this interval does not depend on a server-local daily clock. Event `targetDate` remains metadata; reminders use item `dueDate`.

A delivery key combines item ID and the full due timestamp. The key has a unique database index. Renaming an item or marking a notification read does not send another reminder for that occurrence. Advancing a recurring chore's due date creates a new occurrence.

The application persists an unread in-app notification before attempting email. If SMTP succeeds, it marks that notification read, retaining its delivery key. If mail fails or the process stops, the inbox notification remains available; automatic scans do not repeatedly send email. SMTP and SQLite cannot form one atomic transaction: this deliberately favors a durable inbox fallback and at most one automatic mail attempt over possible duplicate mail. There is no automatic SMTP retry queue.

SMTP is enabled when `MAIL_HOST` is nonblank and the active owner has an email address. For authenticated submission, configure `MAIL_AUTH`, `MAIL_STARTTLS`, and `MAIL_FROM` as described in the [handbook](../deployment/self-hosting-handbook.md). Deactivated owners are skipped.

Tests cover due items, completed exclusions, repeated scans, SMTP deduplication, and overdue catch-up. Large installations may eventually need pagination and a durable outbound-mail queue; the current target remains a small private instance.
