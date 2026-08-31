# MVP Roadmap

The detailed requirements spine now lives under `docs/product/`, `docs/domain/`, `docs/architecture/`, and `docs/planning/`.

## Phase 0: Scaffold

Status: complete in the initial repository scaffold.

- Repository skeleton
- Spring Boot backend shell
- Vue frontend shell
- Single-container Docker build

## Phase 1: Persistence

Status: complete for MVP foundation.

- Flyway SQLite schema
- JPA entities and repositories

## Phase 2: Auth and settings

Status: complete for auth, settings, and initial backend i18n.

- Session auth
- First-user admin bootstrap
- Registration toggle
- Backend i18n

## Phase 3: Lists and items

Status: complete for owner list/item CRUD, type-specific MVP validation, and internal read-only sharing.

- Owner-isolated list CRUD
- Item CRUD
- Type-specific WISH/CHORE/EVENT form and API validation
- Internal read-only sharing

## Phase 4: Public sharing

Status: complete for public token lifecycle, safe guest read DTOs, and one-time wishlist claiming.

- Cryptographic share tokens
- Guest read endpoint
- Guest claim endpoint

## Phase 5: Scraping

Status: complete for direct URL metadata preview and async URL-only item enrichment.

- Jsoup metadata scraping
- Async URL-only item enrichment

## Phase 6: Reminders

Status: complete for item due-date scanning, SMTP-or-in-app delivery, unread notification API, and basic frontend notification list.

- Notifications
- SMTP-or-in-app graceful fallback

## Phase 7: Frontend MVP

Status: complete for MVP auth/list/detail/type/share/admin workflows.

- Auth screens
- Dashboard
- List detail
- Type-specific forms
- Share page
- Admin/settings
- Admin user listing

## Phase 8: Release verification

- Docker smoke script
- README quickstart
- Security test matrix

## Implementation slices

Use [planning/implementation-slices.md](planning/implementation-slices.md) as the active development queue. It converts this roadmap into vertical testable slices.