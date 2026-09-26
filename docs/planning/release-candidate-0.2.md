# 0.2.0-rc.1: planning and reusable lists

The release candidate combines the September reliability/security repairs, email recovery fixes and holistic workspace redesign with milestones 2–4 from the revised roadmap.

## Today & upcoming
The authenticated overview combines open dated items and event dates from active lists owned by or shared with the user. Overdue means before the beginning of the user's local calendar day; Today covers that day; upcoming covers the next seven calendar days. Day boundaries use an IANA timezone and handle daylight saving. Undated work remains in the list workspace. Read-only users can open entries; owners/contributors can complete eligible items. Stale overview revisions must be refreshed before completion. Recurring chore completion still advances its next due date.

## Archive and recovery
Archive pauses a list without deleting its data. It disappears from active lists, collaborator access and future reminder scans; the owner can inspect it read-only and restore it. Deleting lists/items and clearing completed groceries moves them to persistent Trash. The latest deletion/archive has an immediate Undo action; Trash recovery also works after reload, logout or restart. Recovery is owner-only. Restoring a deleted list preserves remaining items and internal permissions; independently deleted items remain in Trash. Public links are revoked on archive/delete and never resurrected by restore. There is no automatic Trash purge in this candidate. Existing inbox/email notification history is retained.

## Private templates
Save an owned list as a named personal template. The blueprint is private, hidden from active lists and reminders, and cannot be publicly/internally shared. Edit its items through Open list. Use it to create independent lists with new IDs, no copied shares/public tokens, open items, no guest reservations or completion history, and no copied item due dates. Content, quantities, categories, prices/currencies, responsibility labels and recurrence definitions are retained. Event instances require a fresh target date; unscheduled recurring chores need a new item date before reminders can run. Templates can be moved to Trash and restored without affecting existing instances. Existing Duplicate list retains its older exact-copy behavior.

## Upgrade and compatibility
Back up the full database before upgrade. Migration V14 includes reliability repairs and V15 adds archive/template/deletion state and list revisions. Rollback to earlier schemas requires the matching prior database snapshot. Item PUT requires `If-Match: "<version>"`. DELETE now hides records in Trash instead of physically removing them; direct reads and public/active queries do not return trashed records. Template item dates are deliberately reset, not inferred from old events.

## Acceptance gates
- Full backend suite, including authorization, local-day/DST boundaries, restore revisions, reminder suppression and fresh template/event behavior.
- Frontend tests and production type-check/build.
- Playwright desktop/mobile: existing journeys plus Today completion/stale rejection, archive/Undo/read-only preview, persistent item/list recovery, and template creation/instance/recovery.
- Full Docker smoke and isolated database restore; backup, Compose, script and documentation contracts.
- Candidate image and checksums correspond to the committed release source. No live SQLite data, SMTP secrets or private deployment configuration are release assets.
