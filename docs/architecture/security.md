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
- Store token server-side.
- Revocation invalidates old token.
- Re-enable creates a new token.
- Current weak point: public share tokens are bearer secrets and are still stored as raw token values in the application database. Hashing public share tokens at rest is future hardening.

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
- Resolve the target host before fetching and reject loopback, link-local, site-local/private, Carrier Grade NAT, IPv6 unique-local, multicast, and metadata-service addresses unless `SCRAPER_ALLOW_PRIVATE_ADDRESSES=true` is deliberately set for a trusted test/dev deployment.
- Connect the scraper socket to the validated address rather than handing hostnames back to the HTTP client for a second DNS lookup.
- Disable automatic redirects; each redirect target is resolved, checked, and connected through the same validated-address path before the scraper follows it.
- Apply timeout and bound downloaded HTML to 1 MiB.
- Do not persist full scraped HTML.

## Admin boundaries

Admins manage instance settings and users. MVP admins do not automatically have content-superuser access to every private list.

## Security logging

The app records structured security events for filter-level rejects such as oversized request bodies, rate limits, and CSRF failures in the `security_events` table and logs them as `security_event` lines. Future work can extend the same service to admin/user/public-share lifecycle events.

## Current weak points and future hardening

- Alice currently runs as a private Tailnet HTTP deployment. Internet-facing use should add HTTPS/TLS termination, verify live HSTS, and set `SESSION_COOKIE_SECURE=true`.
- Public share tokens are high-entropy and revocable, but stored raw at rest; migrate to hashed public-token storage before broader exposure.
- Audit logging currently covers filter-level rejects. Extend it to admin changes, auth lifecycle, user lifecycle, and public token generation/revocation.
- CI includes OSV dependency scanning, but release images/JARs are not signed and no SBOM artifact is published.
- GitHub Actions currently pass but emit upstream deprecation warnings for some action runtimes; upgrade action majors as maintenance work.
- Backups, host encryption, and host firewall policy are deployment-environment responsibilities, not enforced by this application repository.