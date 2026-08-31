# Security Architecture

## Principles

- Private by default.
- Centralized authorization.
- Separate public and internal data shapes.
- Treat all external URLs and public-token traffic as untrusted.

## Authentication

MVP uses Spring Security session cookies.

Rules:

- Passwords are hashed with BCrypt.
- Login creates a server-side session.
- Logout invalidates the session.
- The frontend uses `credentials: "include"`.

## CSRF

Because sessions are cookie-based, mutating authenticated API calls need CSRF handling.

Implementation direction:

- Use Spring Security CSRF protection for authenticated browser calls.
- Expose CSRF token in the standard Spring-friendly way for the SPA.
- Public unauthenticated guest claim endpoint needs a deliberate CSRF decision; for MVP it should validate token context and strict JSON body, and can later gain rate limiting.

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
- Apply timeout.
- Do not persist full scraped HTML.
- Consider SSRF protections before exposing this beyond trusted self-hosted use.

## Admin boundaries

Admins manage instance settings and users. MVP admins do not automatically have content-superuser access to every private list.

## Future hardening

- Rate limiting for login and guest claims.
- Audit log for admin changes and public token generation.
- Optional reverse-proxy trusted headers configuration.
- Content Security Policy for SPA static assets.