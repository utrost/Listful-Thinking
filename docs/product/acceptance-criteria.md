# MVP Acceptance Criteria

These criteria define the minimum behavior that must be proven by tests or smoke scripts before calling the MVP complete.

## Startup and deployment

- The app starts with no required external services.
- The app starts when only default environment variables are present.
- The runtime container runs as UID/GID `1000:1000`.
- SQLite database is created under `/app/data`.
- Docker Compose uses exactly one persistent app data volume.

## Authentication and bootstrap

- If no users exist, `POST /api/v1/auth/register` creates the first user as `ADMIN` even when registration is disabled.
- If users exist and registration is disabled, public registration returns a localized error.
- If registration is enabled, later users register as `USER`.
- Login creates an authenticated session cookie.
- Logout invalidates the session.
- `GET /api/v1/auth/me` returns current user identity and role.
- A signed-in user can change password by supplying the current password; old sessions and outstanding account links stop working.
- An admin-issued setup/recovery link is one-time, hashed at rest, expires after 24 hours, and lets the user choose a password. The raw link is never recorded in audit details.

## Admin settings

- Admins can list users.
- Admins can create users while public registration is disabled.
- Admins can create either `USER` or `ADMIN` accounts.
- Admins can create an account without choosing its password and reissue a setup/recovery link for an active user. The response contains a link only if it was not emailed.
- Admins can deactivate and reactivate users.
- Deactivated users cannot log in or use email auth tokens.
- Admins can view per-user list counts, but cannot view private list content without an explicit share.
- An admin can transfer all lists from an inactive owner to a different active account, including archived lists, templates, and Trash; the admin receives a count without private titles/content and cannot transfer them to themselves.
- Admins can read and update global settings.
- Non-admin users cannot access admin endpoints.
- Database settings override environment defaults where applicable.

## Multi-tenancy

- A registered user sees only owned lists plus lists explicitly shared with them.
- A registered user cannot access another user's private list by guessing a UUID.
- A registered user cannot mutate a list they neither own nor have explicit rights to.
- API responses never serialize JPA entities directly.

## Lists and items

- Owners can create, update, and delete `WISH`, `TODO`, `GROCERY`, `CHORE`, and `EVENT` lists.
- Owners can add, update, and delete items in owned lists.
- `WISH` items support URL, image URL, and price.
- `TODO` items support due date/time and reject shopping fields.
- `GROCERY` items support quantity and category, and reject shopping, due-date, and recurrence fields.
- `CHORE` items support due date and recurrence rule.
- `EVENT` lists support target date and item due dates.
- Owners can edit existing items.
- Owners can mark TODO, GROCERY, CHORE, and EVENT items `DONE` and reopen them to `OPEN`.
- WISH items keep gift statuses (`OPEN`, `CLAIMED`, `PURCHASED`) and reject `DONE`.

## Internal sharing

- Owners can share a list with another registered user by username.
- Shared users can read the shared list and items.
- Existing/missing-permission shares default to `READ`.
- `CONTRIBUTE` shared users can add, edit, complete, and reopen supported items.
- Owners can revoke internal sharing.
- Active recipients receive in-app notifications when shares are added, changed, or revoked.
- An owner can transfer a list to another active account. The old owner loses access, unrelated internal shares remain, a redundant share for the new owner is removed, the public link is revoked, both owners are notified, and an audit event is recorded.

## Public sharing

- Owners can generate a public token for one list.
- Owners can choose public mode `VIEW`, `WISH_CLAIM`, or `SIGNUP`.
- Public tokens are URL-safe and generated with at least 128 bits of entropy.
- Revoking public sharing makes the old token unusable.
- Public links expire 56 days after issue and return `404` for reads/claims after expiry.
- Trashed lists, templates, and items are permanently purged after 14 days; operator backups have an independent retention policy.
- Public list responses do not expose owner email, internal user IDs, private share records, or admin fields.

## Guest claims

- Guests can read a public list without authentication.
- Guests can claim/sign up for an `OPEN` item by providing a non-empty guest name only when the public link mode allows it.
- `VIEW` links do not allow guest claims/signups.
- `WISH_CLAIM` links allow wishlist item claims only.
- `SIGNUP` links allow non-wishlist item signup only.
- Claiming sets `status=CLAIMED` and `reserved_by_guest`.
- Guests cannot claim already claimed or purchased items.
- Guests cannot claim an item from another list using a valid token for a different list.

## Scraping

- `POST /api/v1/utils/scrape` rejects non-HTTP(S) URLs.
- Scraping uses browser-like User-Agent and Accept headers.
- Scraping extracts title, description, image, and price on a best-effort basis.
- URL-only item creation creates an item immediately and enriches asynchronously.
- Scraping failure never rolls back item creation.

## Reminders and notifications

- The minute-based reminder scan detects unfinished due items within the next 24 hours, including overdue items; event target dates alone do not trigger reminder mail.
- Done items do not trigger due-item reminders.
- When mail delivery succeeds, the reminder is sent by email.
- An in-app notification is persisted first and remains unread when mail is unavailable or fails.
- Repeated scans do not create duplicate reminders for the same item and full due timestamp.

## Internationalization

- Frontend ships English and German locale JSON files.
- Frontend auto-detects German browser language and falls back to English.
- Backend error messages are selected from `Accept-Language` with `SYSTEM_LANG` fallback.
- Email/notification message templates use backend message keys.

## Final smoke path

A smoke script must prove this path through real HTTP calls:

1. Start Docker container with fresh data volume.
2. Register first admin.
3. Login.
4. Create WISH list.
5. Add URL-only item.
6. Generate public share link.
7. Fetch public share as guest.
8. Claim item as guest.
9. Verify SQLite file exists under `/app/data`.

## Current browser and release gates

- Desktop and mobile Playwright journeys cover first admin setup, user login/recovery/password change, sharing permissions and notifications, owner transfer, and inactive-owner admin handoff against an isolated Docker database.
- CI must pass backend, frontend, contracts, container smoke, GUI smoke, and strict dependency scanning before merge. Live deployment verification checks readiness, current assets, Tailnet-only binding, preserved settings, and SQLite integrity/foreign keys and counts.

## V18 website capture candidate checks

These checks apply to `feature/web-capture-imports`; Alice remains on V17 until a separate release:

- The authenticated extraction endpoint accepts public HTTP(S) pages through the guarded fetcher and returns a typed draft without creating list data. Event subtypes such as `MusicEvent` are recognized.
- Users can edit product fields, recipe/how-to lines, and event date/location before saving. An unreadable public page offers a manual draft that preserves the source link; authentication and rate-limit failures remain visible errors.
- Imported items/lists persist reviewed source and type-specific fields. An advisory warning identifies a matching wishlist product URL after common tracking parameters are removed; a deliberate duplicate remains possible.
- A failed line import leaves unsaved lines available to retry. A partially saved event import does not create a second event list on retry.
- Guest claim-control tokens are stored hashed; only the matching claimant can rename/release while the public link is active. The owner can release a mistaken claim, and ordinary wishlist reads hide guest names.
- Desktop/mobile deterministic Playwright journeys and opt-in live-page tests use an isolated Docker database. V18 must be tested against a restored backup before any persistent upgrade.
