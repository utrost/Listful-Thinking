# Reminders and Notifications

This documents the implemented MVP behavior, not future calendar integrations.

## Daily reminder scan

Spring scheduling is enabled. `ReminderService` runs once per day at `06:15` container-local time via `@Scheduled(cron = "0 15 6 * * *")`.

The scan checks TODO, CHORE, and EVENT items with:

- `dueDate` present,
- due date from `now` inclusive to `now + 24 hours` exclusive,
- status other than `PURCHASED`.

`targetDate` is currently list metadata for event lists. MVP reminder delivery is item `dueDate` based.

## Delivery policy

For each upcoming due item:

1. Build a deterministic daily reminder key from item ID, item name, and UTC due date.
2. Skip if an unread notification with the same key already exists for that user.
3. If SMTP is configured and the user has an email address, send an email reminder.
4. If SMTP is absent, the user has no email, or email sending fails, create an in-app notification.

SMTP is considered configured when `spring.mail.host` / `MAIL_HOST` is non-blank.

## In-app notifications

The in-app notification API exposes unread notifications only:

- `GET /api/v1/notifications`
- `PUT /api/v1/notifications/{id}/read`

Messages are localized through Spring `MessageSource` using `Accept-Language`.

Implemented message key:

- `notification.item_due_soon`

## Known MVP simplifications

- Notification dedupe is based on unread notification records. If a user marks a reminder read before the item is no longer due, a later scan may create a fresh reminder.
- Email reminders use a compact plain-text message.
- There is no per-user timezone preference yet; due-day dedupe uses UTC.
