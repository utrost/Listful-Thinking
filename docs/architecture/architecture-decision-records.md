# Architecture Decision Records

This file captures early architecture decisions. Add new ADRs as sections; do not rewrite history silently.

## ADR-001: Use Vue 3 for the frontend

Decision:

- Use Vue 3 with Composition API and Vite.

Reason:

- The project specification explicitly asks for `vue-i18n` support from day one.
- Vue gives a straightforward typed SPA structure without much framework ceremony.

Consequences:

- Frontend i18n is JSON-file based.
- Future UI slices should prefer small Vue components and typed API clients.

## ADR-002: Use Spring Security sessions for MVP

Decision:

- Use stateful session cookies rather than JWT for MVP.

Reason:

- Simpler self-hosted deployment.
- Spring Security supports this path directly.
- Avoids token refresh, revocation, and storage complexity.

Consequences:

- CSRF handling must be deliberate for mutating endpoints.
- Frontend fetch calls use `credentials: "include"`.

## ADR-003: Use SQLite with Flyway migrations

Decision:

- Use SQLite as the only database and Flyway for schema migrations.

Reason:

- SQLite satisfies the zero-config self-hosting goal.
- Flyway provides explicit reproducible schema changes.
- Avoid relying on Hibernate auto-DDL for production schema.

Consequences:

- UUIDs are stored as `TEXT`.
- Timestamps should use a consistent representation.
- Migrations must be SQLite-compatible.

## ADR-004: Keep public DTOs separate from internal DTOs

Decision:

- Public share endpoints use dedicated DTOs and never serialize entities directly.

Reason:

- Public links are intentionally unauthenticated.
- Internal IDs, owner metadata, and share records must not leak.

Consequences:

- More DTO mapping code.
- Safer API boundaries and easier tests.

## ADR-005: Default Docker Compose storage is a named volume

Decision:

- Compose uses `listful-data:/app/data` by default.

Reason:

- A bind mount such as `./data:/app/data` can be created by Docker as root on the host and fail with a non-root container.
- Named volumes work zero-config with `USER 1000:1000`.

Consequences:

- README documents optional bind-mount setup for manual file-level backup.

## ADR-006: Scraping is asynchronous and best-effort

Decision:

- URL-only item creation persists a placeholder item and enriches it asynchronously.

Reason:

- Scraping is unreliable and site-dependent.
- Users should not lose the item just because metadata extraction failed.

Consequences:

- Item UI should handle placeholder and later refresh.
- Scraper failures are logged/controlled, not fatal to item creation.

## ADR-007: Reuse account tokens and transfer ownership transactionally

Decision:

- Admin-created accounts may use a one-time setup link instead of an admin-chosen password. Setup/recovery uses the existing hashed password-reset token flow with a 24-hour expiry.
- List owner changes run in one backend service transaction that also revokes the public link, removes a redundant share for the new owner, writes notifications, and requires an audit row.
- Admin handoff is limited to inactive source accounts and cannot target the acting admin; administrators receive only a transferred count.

Reason:

- A new account holder should choose their own password, including when SMTP is unavailable.
- Ownership changes must keep list data and sharing consistent without granting admins routine access to private content.

Consequences:

- The handoff release needs no migration beyond V17; rollback to the prior V17 image does not require a schema downgrade.
- Existing sessions and account links are invalidated when a password is set or changed. Owners must issue a fresh public link after transfer.

## ADR-008: Review website drafts before creating list data

Decision:

- The authenticated extraction API returns a typed, transient draft using the existing validated-address fetcher. The browser lets the user edit that draft, then calls normal list/item APIs to persist selected fields.
- Persist source URLs and selected product/event metadata in V18. Store each guest claim-control token as a hash and return the raw token only to the claimant.

Reason:

- Website metadata can be incomplete, localized by redirects, or simply wrong; product prices and event dates need human review.
- Reusing list/item services keeps type validation, authorization, and sharing behavior in one place.
- Claim correction should not expose wishlist claimant names or require a guest account.

Consequences:

- Page capture is a snapshot. There is no background synchronization or price monitoring.
- Multi-item imports can partially succeed; the UI preserves remaining lines for retry. A duplicate warning is advisory.
- V18 is a forward SQLite migration; a pre-V18 database snapshot is needed for a tested rollback to V17.
