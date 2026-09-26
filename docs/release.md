# Release Verification

Use this checklist before tagging or publishing a Listful Thinking image.

## Local quality gates

Run from the repository root:

```bash
cd backend && mvn test
cd ../frontend && npm test && npm run build && npm run test:gui
cd .. && scripts/test-smoke-contract.sh && scripts/test-compose-env-contract.sh && scripts/test-docs-contract.sh && python3 scripts/test-backup.py
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
- item responsibility labels (`ownerLabel`, `assistantLabels`) for work-style list items
- public share token creation with explicit `WISH_CLAIM` and `SIGNUP` modes
- unauthenticated guest wishlist claim and non-wishlist signup
- SQLite DB file under `/app/data`

## Manual quickstart and production profile checks

For the exact README quickstart path:

```bash
docker compose up --build
```

Then open <http://localhost:8080>, register the first admin, and verify the workspace loads.

For the production self-hosting profile, verify the example env file renders and the health endpoint works:

```bash
docker compose --env-file .env.example -f compose.prod.yml config
mkdir -p data
sudo chown 1000:1000 data
docker compose --env-file .env.example -f compose.prod.yml up --build -d
curl -fsS http://localhost:8080/api/v1/health
docker compose --env-file .env.example -f compose.prod.yml down
```

The production profile is documented in [Self-Hosting Handbook](deployment/self-hosting-handbook.md).

## Alice Tailnet deployment

The private Alice deployment is not driven by the repository Docker Compose file. It is a direct single-container Tailnet deployment that preserves the existing container name, Tailscale-only port binding, restart policy, and persistent SQLite volume.

Use [Alice Tailnet Deployment](deployment/alice-tailnet.md) when deploying to Alice.
Use [Self-Hosting Handbook](deployment/self-hosting-handbook.md) for general private/Tailnet installs and public HTTPS reverse-proxy deployments. At minimum, verify:

- `listful-thinking-alice` is recreated from the merged `listful-thinking:alice` image.
- `100.123.149.120:8080->8080/tcp` remains the only app port binding.
- `/api/v1/health` returns `{"status":"ok"}`.
- The root page loads and the production JS asset contains markers for the changed feature.
- For schema releases, the live SQLite database has the expected Flyway version and columns after deployment.
- The LAN IP does not answer on port `8080` while the service is meant to be Tailnet-only.

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
- Responsibility labels are preserved through item create/update/read paths and remain authorization-neutral.

Known weak points and future hardening:

- Alice/private deployment is Tailnet HTTP; public internet deployment needs HTTPS/TLS termination, verified HSTS, and `SESSION_COOKIE_SECURE=true`.
- Public share token hashes are stored at rest; raw bearer tokens are returned only at generation time and legacy raw rows are migrated to hashes.
- Structured audit rows currently cover filter-level rejects; extend to admin/auth/user/public-share lifecycle events.
- CI has OSV scanning, but release images/JARs are not signed and no SBOM artifact is published.
- Current GitHub Actions are green but emit action-runtime deprecation warnings; upgrade action majors as maintenance work.
- `npm audit` fails closed when the npm registry audit endpoint is unavailable. That is the desired security posture, but it can create transient red frontend jobs during registry 503/timeout incidents.

## Review remediation release checks

Run `scripts/smoke.sh` (now includes an isolated database restore), `python3 scripts/test-backup.py`, and `scripts/test-docs-contract.sh` alongside backend/frontend/browser suites. Migration V14 adds item revisions, import/currency state, notification delivery keys, and historical audit-path redaction. Make a verified backup before release. Custom item API clients must send the returned version in `If-Match` on PUT.

## 0.2.0-rc.1 verification — 2026-09-26

The candidate combines all September review repairs, email recovery fixes, the workspace redesign and roadmap milestones 2–4. See [release notes](releases/0.2.0-rc.1.md).

- Backend: 120 tests passed, including 10 planning/lifecycle tests covering DST boundaries, authorization, reminder suppression, Trash recovery, public-link revocation, concurrent archive writes and fresh templates. Two additional import tests verify contention retries in fresh transactions and preservation of concurrent user edits.
- Frontend: 63 tests passed; the candidate Docker build passed TypeScript checking and production compilation. Production npm audit reports zero vulnerabilities.
- Browser acceptance: desktop/mobile Playwright covers the existing experience plus Today, stale completion rejection, archive/Undo, persistent list/item recovery and template creation/use/recovery. **20 passed, 6 deliberate duplicate-platform skips**, with no failures.
- Full Docker API smoke passed, including non-root runtime, revision-checked updates, explicit unavailable-email behavior, backup integrity and restored-container login/list access.
- Script, Compose and documentation contracts, standalone online backup checks and whitespace checks passed.

The browser setup waits for database readiness and stops the previous disposable stack before recreating its database. Tests use isolated databases and ports; live credentials/data are excluded from all public artifacts.

## 0.2.0-rc.2 security verification — 2026-09-26

See the [security review](security-review-2026-09-26.md) and [RC2 release notes](releases/0.2.0-rc.2.md). The full backend suite passed 124 tests; the final SSRF additions also passed the focused security suite. Frontend tests passed 66 checks and production type-check/build. The full desktop/mobile suite passed 24 journeys with 6 intentional skips, including image-request interception and draft clearing on logout. Full Docker API smoke and isolated restore passed. The 77-component resolved runtime inventory had zero OSV advisory matches. Version/copy-only packaging is verified with focused browser checks and CI.
