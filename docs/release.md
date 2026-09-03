# Release Verification

Use this checklist before tagging or publishing a Listful Thinking image.

## Local quality gates

Run from the repository root:

```bash
cd backend && mvn test
cd ../frontend && npm test && npm run build
cd .. && scripts/test-smoke-contract.sh
```

## Container smoke

The smoke script builds a fresh single-container image through Docker Compose, starts it on an isolated port, and exercises the real HTTP API with cookies and the SQLite volume mounted.

```bash
scripts/smoke.sh
```

Optional knobs:

```bash
LISTFUL_SMOKE_PORT=18082 scripts/smoke.sh
LISTFUL_KEEP_SMOKE=true scripts/smoke.sh
```

The smoke covers:

- `/api/v1/health` readiness
- runtime UID/GID `1000:1000`
- first-user admin bootstrap
- second-user registration
- admin user list without password hashes
- registration setting toggle
- owner list creation
- item creation
- public share token creation
- unauthenticated guest claim
- SQLite DB file under `/app/data`

## Manual quickstart check

For the exact README path:

```bash
docker compose up --build
```

Then open <http://localhost:8080>, register the first admin, and verify the workspace loads.

## Security test matrix

Implemented automated coverage:

- Password hashes are not returned from auth or admin user APIs.
- Passwords are persisted only as salted BCrypt hashes; same plaintext passwords produce different stored hashes.
- Password reset stores a fresh salted BCrypt hash and invalidates the old plaintext match.
- First registered user is `ADMIN`; later users are `USER` when registration is enabled.
- Non-admin users cannot access `/api/v1/admin/**`.
- Registration-disabled errors are localized.
- Owner list and item APIs reject guessed IDs from other users.
- Internal sharing is read-only for the recipient.
- Public share DTOs exclude owner/admin/internal data.
- Revoked public tokens fail.
- SQL-injection-shaped login usernames and public-share tokens do not authenticate or resolve records.
- Duplicate guest claims return conflict.
- Oversized API JSON bodies return `413 payload_too_large` before controller parsing.
- Sensitive POST endpoints return `429 rate_limited` after too many requests from one client/window.
- Scraper rejects non-HTTP(S) schemes, private/local/metadata network targets, unsafe redirect targets, and caps downloaded HTML at 1 MiB.
- Browser-style authenticated mutations require `X-CSRF-TOKEN`; the SPA fetches it from `/api/v1/auth/csrf`.
- API responses include CSP, referrer policy, and permissions policy headers.
- Filter-level security rejects are written to `security_events`.
- Notifications are owner-scoped.

Known future hardening:

- Hash public share tokens at rest.
- Extend audit logging to admin/auth/public-share lifecycle events.
- HTTPS/HSTS and `SESSION_COOKIE_SECURE=true` for internet-facing deployments.
- Signed release images/SBOM publication.
