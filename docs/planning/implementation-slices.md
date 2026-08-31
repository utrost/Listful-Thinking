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

User outcome:

- Admin can manage registration and users from UI.

Frontend:

- Admin screen.
- Route gating by role.

Tests:

- Frontend smoke tests for key routes/components.

## Slice 15: Docker smoke and release docs

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