# Security Architecture

## Principles

- Private by default.
- Centralized authorization.
- Separate public and internal data shapes.
- Treat all external URLs and public-token traffic as untrusted.

## SQL injection posture

Listful does not build SQL from user-provided strings. Database access uses Spring Data/JPA derived queries or JPQL with named parameters such as `:userId`, `:start`, and `:excludedStatuses`. Security regression tests send SQL-injection-shaped login usernames and public-share tokens and verify they do not authenticate or resolve records.

## Request-size and rate limiting

A servlet hardening filter rejects oversized API request bodies before JSON parsing and rate-limits sensitive POST endpoints per client IP, method, and path. The default runtime settings are:

- `MAX_REQUEST_BODY_BYTES=65536`
- `RATE_LIMIT_ENABLED=true`
- `RATE_LIMIT_MAX_REQUESTS=60`
- `RATE_LIMIT_WINDOW_SECONDS=60`
- `RATE_LIMIT_MAX_BUCKETS=10000`
- `TRUST_FORWARDED_FOR=false`

Covered sensitive endpoints include login, registration, magic-link/password-reset request and consume routes, authenticated scrape requests, and public guest claims. By default the filter uses the socket remote address. Set `TRUST_FORWARDED_FOR=true` only when a trusted reverse proxy strips inbound spoofed headers and sets the real client IP; then the filter uses the first `X-Forwarded-For` value. The rate-limiter bucket map is bounded by `RATE_LIMIT_MAX_BUCKETS` and fails closed when exhausted.

## Authentication

MVP uses Spring Security session cookies.

Rules:

- Passwords are stored only as salted BCrypt hashes; the plaintext password is never persisted.
- BCrypt verification is used for login and password-reset replacement hashes.
- Login creates a server-side session.
- Logout invalidates the session.
- The frontend uses `credentials: "include"`.

## CSRF

Because sessions are cookie-based, mutating authenticated API calls need CSRF handling.

Implementation direction:

- Use Spring Security CSRF protection for authenticated browser calls.
- Expose CSRF token in the standard Spring-friendly way for the SPA.
- Public unauthenticated guest claim endpoint uses token context, strict JSON body, request-size limits, and rate limiting; it still needs a deliberate CSRF decision if later reused by authenticated browser flows.

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
- Store token server-side.
- Revocation invalidates old token.
- Re-enable creates a new token.

## Public data exposure

Public DTOs must exclude:

- Password hashes.
- Owner email.
- Internal user IDs.
- Internal share records.
- Admin role/settings.
- Notification data.

## Scraping security

- Accept only HTTP and HTTPS URLs.
- Do not support `file:`, `ftp:`, or local-path inputs.
- Apply timeout and bound downloaded HTML to 1 MiB.
- Do not persist full scraped HTML.
- Consider SSRF protections before exposing this beyond trusted self-hosted use.

## Admin boundaries

Admins manage instance settings and users. MVP admins do not automatically have content-superuser access to every private list.

## Future hardening

- CSRF token flow for authenticated browser mutations.
- Audit log for admin changes and public token generation.
- Optional reverse-proxy trusted headers configuration.
- Content Security Policy for SPA static assets.