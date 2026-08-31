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

Status: owner-isolated list and item CRUD complete; internal sharing pending.

- Owner-isolated list CRUD
- Item CRUD
- Internal sharing

## Phase 4: Public sharing

- Cryptographic share tokens
- Guest read endpoint
- Guest claim endpoint

## Phase 5: Scraping

- Jsoup metadata scraping
- Async URL-only item enrichment

## Phase 6: Reminders

- Notifications
- SMTP-or-in-app graceful fallback

## Phase 7: Frontend MVP

- Auth screens
- Dashboard
- List detail
- Type-specific forms
- Share page
- Admin/settings

## Phase 8: Release verification

- Docker smoke script
- README quickstart
- Security test matrix

## Implementation slices

Use [planning/implementation-slices.md](planning/implementation-slices.md) as the active development queue. It converts this roadmap into vertical testable slices.