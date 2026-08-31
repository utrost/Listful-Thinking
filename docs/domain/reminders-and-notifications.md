# Reminders and Notifications

## Goal

Reminder behavior must work with zero configuration and become richer when SMTP is configured.

## Reminder sources

MVP reminder candidates:

- Items with `due_date` within the next 24 hours.
- Event lists with `target_date` approaching.
- Future: recurring chores when a recurrence engine exists.

## Scheduler

Default schedule:

- Daily at 08:00 server-local time.
- Implemented with Spring `@Scheduled`.

Future extension:

- Instance-level configurable schedule.
- User-level time zones.

## Email vs in-app fallback

SMTP is considered enabled only when all required values exist:

- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USER`
- `MAIL_PASS`

If SMTP is complete:

- Send localized email through Spring Mail.

If SMTP is missing or incomplete:

- Create an in-app notification row.

## Notification data model

Store:

- User ID.
- Message key.
- Message args as JSON string.
- Created timestamp.
- Optional read timestamp.
- Optional dedupe key in a future migration if needed.

## Localization

Store message keys and args rather than rendered text. Render text according to current user language or `Accept-Language`.

## Deduplication

Reminder generation must avoid duplicate messages for the same user/entity/day.

Implementation options:

- Deterministic message args and lookup before insert.
- Future `dedupe_key` column with unique index.

## MVP simplification

The MVP may check only fixed time windows and simple due dates. Full RFC recurrence expansion is not required before chore recurrence behavior is explicitly implemented.