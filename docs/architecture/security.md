# Security Architecture

This document describes implemented controls and known gaps. For a cross-project snapshot, see [Current State and Risk Register](../current-state-and-risk-register.md).

## Principles

- Private by default.
- Centralized authorization.
- Separate public and internal data shapes.
- Treat all external URLs and public-token traffic as untrusted.

## SQL injection posture

Listful does not build SQL from user-provided strings. Database access uses Spring Data/JPA derived queries or JPQL with named parameters such as `:userId`, `:start`, and `:excludedStatuses`. Security regression tests send SQL-injection-shaped login usernames and public-share tokens and verify they do not authenticate or resolve records.

## Request-size and rate limiting

A servlet hardening filter rejects oversized API request bodies before JSON parsing and rate-limits sensitive POST endpoints per client IP, method, and path. The default runtime settings are:

- `MAX_REQUEST_BODY_BYTES=5100000` (bounded to accommodate validated wishlist image data URLs)
- `RATE_LIMIT_ENABLED=true`
- `RATE_LIMIT_MAX_REQUESTS=60`
- `RATE_LIMIT_WINDOW_SECONDS=60`
- `RATE_LIMIT_MAX_BUCKETS=10000`
- `TRUST_FORWARDED_FOR=false`

Covered sensitive endpoints include login, registration, magic-link/password-reset request and consume routes, authenticated scrape and typed extraction requests, and public guest claim/control requests. By default the filter uses the socket remote address. Set `TRUST_FORWARDED_FOR=true` only when a trusted reverse proxy strips inbound spoofed headers and sets the real client IP; then the filter uses the first `X-Forwarded-For` value. The rate-limiter bucket map is bounded by `RATE_LIMIT_MAX_BUCKETS` and fails closed when exhausted.

## Authentication

MVP uses Spring Security session cookies.

Rules:

- Passwords are stored only as salted BCrypt hashes; the plaintext password is never persisted.
- BCrypt verification is used for login and password-reset replacement hashes.
- Login creates a server-side session.
- Logout invalidates the session.
- Password change and reset revoke existing sessions through the account session version and delete outstanding account tokens. Admin-issued setup/recovery links use hashed one-time reset tokens with a 24-hour lifetime; the normal self-service reset and magic links expire after 30 minutes.
- The frontend uses `credentials: "include"`.

## CSRF

Because sessions are cookie-based, mutating authenticated browser-style API calls use an app-level CSRF token flow.

Implementation:

- `GET /api/v1/auth/csrf` creates/returns a session token.
- Mutating `/api/v1/**` calls with browser request metadata (`Origin` or `Sec-Fetch-Site`) must include `X-CSRF-TOKEN`.
- Auth bootstrap/link endpoints and public unauthenticated share routes remain exempt; they are separately protected by strict JSON parsing, request-size limits, and rate limiting.
- The SPA API client fetches and caches the token before authenticated mutations.
- Session cookies are explicitly `HttpOnly` and `SameSite=Strict`; `SESSION_COOKIE_SECURE=true` should be set when served via HTTPS.
- This app-level CSRF check is browser-metadata-aware: unsafe API calls without `Origin` and without `Sec-Fetch-Site` are treated as non-browser/API-client calls and are not rejected solely for missing CSRF. The browser protection therefore relies on both the token flow and `SameSite=Strict` cookies.

## Authorization

All list/item access must go through `ListAccessService` or an equivalent central service.

The service should answer questions such as:

- Is actor owner?
- Is actor shared reader?
- Is actor public guest for this token?
- Can actor perform operation?

Controllers should remain thin and should not copy authorization rules.

## Multi-tenancy

- User list queries are scoped to owner or explicit internal share.
- Item access verifies parent list access.
- Resource IDs are not proof of access.
- Prefer 404 when denying access would otherwise reveal private resource existence.

## Public share tokens

- Generate with `SecureRandom`.
- Use URL-safe Base64.
- Use at least 128 bits of entropy.
- Store only a `sha256:` hash of the token in the application database.
- Resolve incoming public tokens by hashing the presented bearer token before lookup.
- Migrate legacy raw database values to hashes during Flyway startup; a compatibility path also hashes a legacy raw value if it is encountered during lookup.
- Revocation invalidates old token.
- Re-enable creates a new token.
- Every public link expires 56 days after issue; a link active during the V17 upgrade received 56 days from that migration.
- Existing public links keep working after migration, but the owner UI can only show the raw bearer token immediately after generating a link; if an owner loses the URL, they should revoke/recreate it.
- Individual guest claims use a separate random control token stored only as a hash. The raw value is returned only to the claimant and permits rename/release only with the matching active public link and item. An owner can release a mistaken claim without receiving that token.

## Public data exposure

Public DTOs must exclude:

- Password hashes.
- Owner email.
- Internal user IDs.
- Internal share records.
- Admin role/settings.
- Notification data.
- Wishlist claimant names in ordinary public reads and owner item reads; a claimant's immediate claim/rename response may include their own name.

## Scraping security

- Accept only HTTP and HTTPS URLs.
- Do not support `file:`, `ftp:`, or local-path inputs.
- Resolve the target host before fetching and reject loopback, link-local, site-local/private, Carrier Grade NAT, IPv6 unique-local, multicast, and metadata-service addresses unless `SCRAPER_ALLOW_PRIVATE_ADDRESSES=true` is deliberately set for a trusted test/dev deployment.
- Connect the scraper socket to the validated address rather than handing hostnames back to the HTTP client for a second DNS lookup.
- Disable automatic redirects; each redirect target is resolved, checked, and connected through the same validated-address path before the scraper follows it.
- Apply timeout and bound downloaded HTML to 1 MiB.
- Do not persist full scraped HTML.
- The typed extraction endpoint reuses the guarded fetch path, limits, and rate limiting. It returns a draft; saving the reviewed fields is a separate authenticated list/item operation. A page redirect can change the suggested source URL to the final destination.

## Admin boundaries

Admins manage instance settings and users. They can issue setup/recovery links and transfer all lists from an inactive account to a different active account, but cannot browse private item content or transfer those lists to themselves. Owners can transfer individual lists to active accounts. Both handoff paths revoke the affected public link and record an audit event.

## Security logging

The app records structured security events for filter-level rejects such as oversized request bodies, rate limits, and CSRF failures in the `security_events` table and logs them as `security_event` lines. It also records selected admin/user, public-link, and ownership-handoff events. Raw bearer and setup links are excluded from audit details; full auth lifecycle coverage remains future work.

## Current weak points and future hardening

- Alice currently runs as a private Tailnet HTTP deployment. Internet-facing use should add HTTPS/TLS termination, verify live HSTS, and set `SESSION_COOKIE_SECURE=true`.
- Public tokens are hashed at rest. Public bearer parameters are redacted from application audit paths; migration V14 redacts historical database audit paths. Operators must separately rotate affected links and handle older external log archives.
- Audit logging covers filter rejects, selected admin user/settings changes, setup-link issuance, ownership handoffs, and public link changes. Auth lifecycle audit coverage remains a follow-up.
- CI includes OSV dependency scanning, and RC2 included a resolved runtime SBOM; release images and JARs are not signed.
- Backups, host encryption, and host firewall policy are deployment-environment responsibilities, not enforced by this application repository.

## Session and edit lifecycle

Successful registration, login, and magic login rotate an existing session ID and clear its CSRF token. Password reset changes the credential hash, invalidates all outstanding account tokens, and causes old sessions to fail the next active-session check. The API client clears its CSRF cache at auth boundaries and retries a recognized CSRF rejection once.

Shared email addresses are supported. Email-only recovery sends a link only for an unambiguous active account; a username selects the account when a family shares an email address. Responses remain neutral.

Item PUT requests require `If-Match: "<version>"` using the version returned by the item API. Missing/stale revisions return `409 stale_item`. Hibernate version checks also protect against changes between checking and committing, and public claims increment the revision. API clients must reload and reconcile rather than silently retry stale edits.

Import workers are limited to two concurrent jobs and a queue of 32. Creating items and retrying imports are rate-limited; queue rejection produces a retryable import state. Public claim requests share a normalized rate bucket instead of one bucket per token/item.
