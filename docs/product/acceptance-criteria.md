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

## Admin settings

- Admins can list users.
- Admins can read and update global settings.
- Non-admin users cannot access admin endpoints.
- Database settings override environment defaults where applicable.

## Multi-tenancy

- A registered user sees only owned lists plus lists explicitly shared with them.
- A registered user cannot access another user's private list by guessing a UUID.
- A registered user cannot mutate a list they neither own nor have explicit rights to.
- API responses never serialize JPA entities directly.

## Lists and items

- Owners can create, update, and delete `WISH`, `CHORE`, and `EVENT` lists.
- Owners can add, update, and delete items in owned lists.
- `WISH` items support URL, image URL, and price.
- `CHORE` items support due date and recurrence rule.
- `EVENT` lists support target date and item due dates.

## Internal sharing

- Owners can share a list with another registered user by username.
- Shared users can read the shared list and items.
- MVP default: shared users are read-only.
- Owners can revoke internal sharing.

## Public sharing

- Owners can generate a public token for one list.
- Public tokens are URL-safe and generated with at least 128 bits of entropy.
- Revoking public sharing makes the old token unusable.
- Public list responses do not expose owner email, internal user IDs, private share records, or admin fields.

## Guest claims

- Guests can read a public list without authentication.
- Guests can claim an `OPEN` item by providing a non-empty guest name.
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

- Daily reminder logic detects due items or upcoming event targets.
- If complete SMTP configuration exists, reminder email is sent.
- If SMTP is absent or incomplete, an in-app notification is created.
- Duplicate notifications for the same entity/day are avoided.

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