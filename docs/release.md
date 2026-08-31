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
- First registered user is `ADMIN`; later users are `USER` when registration is enabled.
- Non-admin users cannot access `/api/v1/admin/**`.
- Registration-disabled errors are localized.
- Owner list and item APIs reject guessed IDs from other users.
- Internal sharing is read-only for the recipient.
- Public share DTOs exclude owner/admin/internal data.
- Revoked public tokens fail.
- Duplicate guest claims return conflict.
- Scraper rejects non-HTTP(S) schemes.
- Notifications are owner-scoped.

Known future hardening:

- CSRF token flow for authenticated browser mutations.
- Rate limits for login, registration, scrape, and guest claim endpoints.
- SSRF host/IP protections for scraping beyond the current HTTP(S)-scheme guard.
- Audit log for admin setting changes and public-share lifecycle.
- Content Security Policy for the packaged SPA.
