# Implementation Slices

This is the preferred implementation order. Each slice should leave the repository green and commit a small coherent change.

## Slice 01: Full SQLite schema

Status: complete.

User outcome:

- The app starts with the real MVP schema in SQLite.

Backend:

- Expand `V1__initial_schema.sql` to all MVP tables and indexes.
- Configure consistent timestamp/UUID storage expectations.

Tests:

- Context loads with Flyway migration.
- Optional schema smoke verifies expected tables exist.

## Slice 02: Domain entities and repositories

Status: complete.

User outcome:

- Backend has typed persistence objects for users, lists, items, settings, shares, and notifications.

Backend:

- Add JPA entities and enums.
- Add repository interfaces.
- Keep entities package-private behavior simple; no controller serialization.

Tests:

- Repository save/load tests for each aggregate.

## Slice 03: Auth bootstrap

Status: complete.

User outcome:

- First visitor can create admin account and log in.

Backend:

- Add auth DTOs/controller/service.
- Add BCrypt password hashing.
- Add session security config.
- Add `GET /auth/me`.

Frontend:

- Add session API client functions.

Tests:

- First user is admin.
- Second registration blocked when disabled.
- Login/logout works.

## Slice 04: Settings and backend i18n

Status: complete.

User outcome:

- Admin can toggle registration and errors can be German/English.

Backend:

- Add `SettingService`.
- Add `MessageSource` config and message bundles.
- Add admin settings endpoints.

Tests:

- Settings override env defaults.
- German `Accept-Language` returns German error.

## Slice 05: Owner-only list CRUD

Status: complete.

User outcome:

- Logged-in user can create and manage own lists.

Backend:

- Add list DTOs/controller/service.
- Introduce `ListAccessService` for ownership checks.

Frontend:

- Dashboard reads/creates lists.

Tests:

- User A cannot see/update/delete User B list.

## Slice 06: Item CRUD by list access

Status: complete.

User outcome:

- Owner can manage items on owned lists.

Backend:

- Add item DTOs/controller/service.
- Every item operation resolves parent list access.

Frontend:

- List detail shows items and item form.

Tests:

- User A cannot mutate User B item by guessed item ID.

## Slice 07: List type behavior

Status: complete.

User outcome:

- Wish/chore/event forms and validation differ appropriately.

Backend:

- Add type-specific validation in DTO/service layer.

Frontend:

- WISH shows URL/image/price.
- CHORE shows due/recurrence.
- EVENT shows target/due dates.

Tests:

- Backend rejects invalid type-specific inputs where MVP requires it.

## Slice 08: Internal read-only sharing

Status: complete.

User outcome:

- Owner can share a list with another local account.

Backend:

- Add `list_shares` service methods and endpoints.
- Extend `ListAccessService` with shared-read access.

Frontend:

- Add owner share panel for usernames.

Tests:

- Shared user can read.
- Shared user cannot edit/delete.
- Owner can revoke.

## Slice 09: Public share tokens

Status: complete.

User outcome:

- Owner can create/revoke a public guest link.

Backend:

- Add `ShareTokenService`.
- Add owner endpoints for public token lifecycle.

Frontend:

- Share panel shows copyable `/s/:token` link.

Tests:

- Token shape and uniqueness.
- Old token fails after revoke.

## Slice 10: Public guest view and claim

Status: complete.

User outcome:

- Guest can open a link and claim an open wishlist item.

Backend:

- Add public share DTOs/controller/service.
- Add claim endpoint with conflict behavior.

Frontend:

- Public share route and guest claim dialog.

Tests:

- Guest sees safe fields only.
- Cross-list claim blocked.
- Duplicate claim returns 409.

## Slice 11: Scraping utility

Status: complete.

User outcome:

- User can preview metadata for a URL.

Backend:

- Add Jsoup scraper and controller.
- Add HTML fixture tests.

Tests:

- OpenGraph extraction.
- JSON-LD price extraction.
- Invalid scheme rejection.

## Slice 12: Async URL-only enrichment

Status: complete.

User outcome:

- Pasting only a URL creates an item and later fills metadata.

Backend:

- Add async configuration.
- Trigger scraper from item creation.

Frontend:

- Show placeholder and refresh/reload behavior.

Tests:

- Scrape success updates item.
- Scrape failure leaves placeholder item.

## Slice 13: Notifications and reminders

Status: complete.

User outcome:

- Due reminders show in app or email if configured.

Backend:

- Add notification controller/service.
- Add reminder scheduler/service.
- Add mail fallback logic.

Frontend:

- Notification bell/list.

Tests:

- SMTP absent creates notification.
- SMTP complete sends mail.
- Dedupe same entity/day.

## Slice 14: Admin and frontend completion

Status: complete.

User outcome:

- Admin can manage registration and users from UI.

Frontend:

- Admin screen.
- Route gating by role.

Tests:

- Frontend smoke tests for key routes/components.

## Slice 15: Docker smoke and release docs

Status: complete.

User outcome:

- A self-hoster can trust the README quickstart.

Repo:

- Add `scripts/smoke.sh`.
- Update README with final behavior.
- Run all quality gates.

Tests:

- `mvn test`
- `npm test`
- `npm run build`
- `docker compose up --build`
- smoke script happy path.

## Post-MVP gap roadmap

The MVP can create typed lists, share them, and notify on due work. The next gaps are about making lists comfortable to work with over time: edit, complete, collaborate, reuse, and recover from mistakes.

### Slice 16: Item lifecycle and status semantics

Status: complete for first lifecycle slice.

User outcome:

- Users can edit existing items and move them through list-appropriate states without deleting/recreating them.

Product gaps covered:

- Item editing is not prominent enough in the UI.
- `PURCHASED`/`CLAIMED` do not map cleanly to TODO, GROCERY, CHORE, or EVENT work.
- Users need done/undone, undo, and hide/show completed flows.

Backend:

- Revisit `ItemStatus` for list-type-specific semantics or add a neutral completion status model.
- Keep wishlist claim/purchase behavior backward compatible.
- Ensure completed items no longer trigger reminder notifications.
- Validate invalid status transitions per list type.

Frontend:

- Add item edit controls for name, description, quantity/category, price, URL, due date, recurrence, and status where relevant.
- Add one-tap done/undone controls for TODO, GROCERY, CHORE, and EVENT items.
- Add hide/show completed toggle per list detail.

Docs:

- Explain status meanings per list type in `docs/user-guide.md`.

Tests:

- Backend tests for status transitions and reminder suppression after completion.
- Frontend tests for edit/status controls.
- Smoke test edits an item and marks it complete.

### Slice 17: List editing and cleanup UX

Status: complete for first list-editing/cleanup slice.

User outcome:

- Users can correct list metadata and safely delete lists after confirmation.

Product gaps covered:

- List editing is under-documented and not obvious.
- Event target dates and descriptions need correction after creation.
- Users need a safe cleanup flow for stale lists.

Backend:

- Confirm/update list update behavior for title, description, type, and target date.
- Decide whether type changes are allowed after items exist.
- Add archive flag if delete is too destructive for common cleanup.

Frontend:

- Add edit-list form in list detail.
- Add delete confirmation that prevents one-click destructive cleanup.
- Surface public/private/share state clearly.

Docs:

- Add list correction, archive/delete, and sharing-consequence sections to the user guide.

Tests:

- Update/delete/archive behavior for owned lists.
- Non-owner and read-only shared users cannot mutate list metadata.

### Slice 18: Shared contributor permissions

Status: proposed.

User outcome:

- A list owner can allow trusted registered users to contribute instead of only reading.

Product gaps covered:

- Internal sharing is too binary.
- Household grocery, chore, todo, and event lists need collaborative check-off/editing.
- Owners need explicit permission levels.

Backend:

- Add a permission column to `list_shares`, for example `READ`, `CONTRIBUTE`, `MANAGE_ITEMS`.
- Extend `ListAccessService` to distinguish read, item contribution, and management operations.
- Preserve read-only behavior for existing shares during migration.

Frontend:

- Let owners choose/update share permission.
- Allow contributors to add/edit/check off items where permitted.
- Keep share/public-link management owner-only unless explicitly expanded.

Docs:

- Update permissions matrix and user guide with permission meanings.

Tests:

- Migration preserves existing read-only shares.
- Contributor can update items but cannot delete the list or manage shares.
- Read-only users remain read-only.

### Slice 19: Grocery shop mode

Status: proposed.

User outcome:

- Grocery lists become usable in a shop: grouped, quick to check off, and easy to reset.

Product gaps covered:

- Grocery currently has quantity/category fields but no shop workflow.
- Users need fast mobile check-off, grouping, clearing, and repeated staples.

Backend:

- Support status/ordering/grouping needed for grocery shop mode.
- Decide whether grocery completion clears, archives, or keeps history.
- Optional: add previously bought/staple hints after current data model is proven.

Frontend:

- Group grocery items by category.
- Add one-tap check-off and hide bought items.
- Add clear completed / re-add common item workflow.
- Optimize the grocery detail screen for mobile use.

Docs:

- Update grocery guide with shop-mode behavior and limitations.

Tests:

- Grocery category grouping and check-off behavior.
- Clear-completed behavior does not affect non-grocery lists.

### Slice 20: Operational recurring chores

Status: proposed.

User outcome:

- Recurring chores advance themselves after completion instead of only storing a recurrence string.

Product gaps covered:

- Recurrence is stored but not operational.
- Users need complete, skip, postpone, pause, and simple recurrence controls.

Backend:

- Parse supported recurrence rules or define a simpler recurrence model.
- On chore completion, calculate the next due date.
- Add skip/postpone behavior.
- Prevent duplicate reminder notifications across recurrence advances.

Frontend:

- Replace raw recurrence text with a simple picker for common schedules.
- Add complete, skip, and postpone controls.
- Show next due date and last-done information.

Docs:

- Document supported recurrence behavior in human terms.

Tests:

- Completing a weekly chore advances to the next due date.
- Skipping/postponing updates reminders correctly.
- Unsupported recurrence patterns fail clearly.

### Slice 21: Reusable templates and list cloning

Status: proposed.

User outcome:

- Users can reuse known-good lists for trips, birthdays, weekly groceries, and recurring events.

Product gaps covered:

- Hard-coded list types are not enough for repeated real-life lists.
- Users need duplicate, save-as-template, and create-from-template flows.

Backend:

- Add clone-list service that copies list metadata and items safely.
- Decide whether public/internal shares are copied; default should be no.
- Add reusable template flag or separate template aggregate if needed.

Frontend:

- Add duplicate list action.
- Add create from template action.
- Let users manage personal templates after clone behavior is proven.

Docs:

- Add examples for packing lists, recurring event plans, and grocery staples.

Tests:

- Clone preserves item fields but creates new IDs.
- Clone does not copy public tokens or internal shares unless explicitly requested.
- Template-created lists remain owner-isolated.

### Slice 22: Flexible public claim/signup modes

Status: proposed.

User outcome:

- Public links can support signup/claim workflows beyond gift wishlists.

Product gaps covered:

- Public sharing is wishlist-only.
- Events and small groups need claimable tasks/slots without accounts.
- Guests need limited correction/contact flows.

Backend:

- Add public link mode such as `VIEW`, `WISH_CLAIM`, `SIGNUP`.
- Allow public claiming for supported non-wishlist list types without leaking private fields.
- Consider guest note/contact and guest unclaim tokens.

Frontend:

- Let owners choose the public link mode.
- Add guest signup/claim UI for supported list types.
- Add owner controls to close/reopen public claiming.

Docs:

- Expand public sharing examples beyond gifts.

Tests:

- Public signup works only for enabled list/link modes.
- Guests cannot claim unsupported items or browse other lists.
- Revocation closes all public modes.

### Slice 23: Search, filter, sort, and review tools

Status: proposed.

User outcome:

- Larger lists remain navigable.

Product gaps covered:

- No search, filtering, sorting, grouping, pinning, or archive review.
- Users need overdue/upcoming/open/done views.

Backend:

- Add query parameters or dedicated endpoints for filtering/sorting if client-side handling becomes too slow.
- Define archive/pin metadata if implemented in slice 17.

Frontend:

- Add item search within a list.
- Add filters for open/completed/claimed/purchased/overdue/upcoming.
- Add sorting by due date, category, status, and created time where relevant.
- Add pinned/archived list affordances if available.

Docs:

- Add list review and cleanup workflow guidance.

Tests:

- Filter/sort state works for each list type.
- Query parameters, if any, are owner/share-authorized.

### Slice 24: Event planning upgrades

Status: proposed.

User outcome:

- Event lists become useful planning boards rather than flat dated checklists.

Product gaps covered:

- Event lists need progress, countdown, sorting, assignment, templates, and optional public signup.

Backend:

- Reuse status, assignment, contributor, clone/template, and signup primitives from earlier slices.
- Add event-specific summary data only after generic item lifecycle works.

Frontend:

- Show countdown to target date.
- Show checklist progress.
- Sort/group event items by due date and completion.
- Support assignment/contribution if slice 18 exists.
- Support clone from previous event if slice 21 exists.

Docs:

- Add event planning examples for birthdays, trips, and parties.

Tests:

- Event progress/countdown data handles missing and past dates.
- Event UI respects owner/contributor/read-only roles.

### Slice 25: User troubleshooting and privacy hardening docs

Status: proposed.

User outcome:

- Users understand what to do when mail, scraping, sharing, or reminders do not behave as expected.

Product gaps covered:

- The user guide lacks a troubleshooting section and a plain privacy model.

Docs:

- Add "Which list type should I choose?" decision tree.
- Add "What happens when I share?" examples.
- Add troubleshooting for missing magic-link email, failed URL scraping, invisible shared lists, revoked public links, and reminders.
- Add privacy model summary: private by default, admin metadata inventory, one-list public links, read-only internal sharing unless permissions are expanded.

Tests:

- Markdown guide links and section anchors remain valid.
- Optional docs contract checks ensure every implemented list type has a guide section.
