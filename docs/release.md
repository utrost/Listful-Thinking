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
- public share token creation with explicit `WISH_CLAIM` and `SIGNUP` modes
- unauthenticated guest wishlist claim and non-wishlist signup
- SQLite DB file under `/app/data`

## Manual quickstart check

For the exact README path:

```bash
docker compose up --build
```

Then open <http://localhost:8080>, register the first admin, and verify the workspace loads.

## Security test matrix

For the full current posture and known weak points, see [Current State and Risk Register](current-state-and-risk-register.md).

Implemented automated coverage:

- Password hashes are not returned from auth or admin user APIs.
- Passwords are persisted only as salted BCrypt hashes; same plaintext passwords produce different stored hashes.
- Password reset stores a fresh salted BCrypt hash and invalidates the old plaintext match.
- First registered user is `ADMIN`; later users are `USER` when registration is enabled.
- Non-admin users cannot access `/api/v1/admin/**`.
- Registration-disabled errors are localized.
- Owner list and item APIs reject guessed IDs from other users.
- Internal sharing supports `READ` recipients and `CONTRIBUTE` recipients; contributor mutations remain item-scoped and list/share management stays owner-only.
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

Known weak points and future hardening:

- Alice/private deployment is Tailnet HTTP; public internet deployment needs HTTPS/TLS termination, verified HSTS, and `SESSION_COOKIE_SECURE=true`.
- Public share tokens are high-entropy bearer secrets but still stored raw in SQLite; hash them at rest in a future migration.
- Structured audit rows currently cover filter-level rejects; extend to admin/auth/user/public-share lifecycle events.
- CI has OSV scanning, but release images/JARs are not signed and no SBOM artifact is published.
- Current GitHub Actions are green but emit action-runtime deprecation warnings; upgrade action majors as maintenance work.
- `npm audit` fails closed when the npm registry audit endpoint is unavailable. That is the desired security posture, but it can create transient red frontend jobs during registry 503/timeout incidents.
