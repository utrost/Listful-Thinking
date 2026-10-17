# Release Verification

Use this checklist before tagging or publishing a Listful Thinking image.

## Public image release gate

The CI workflow now runs a strict high/critical OS and library scan of the built container as well as the existing resolved backend and frontend dependency scans. A `v*` tag publishes an amd64/arm64 GHCR image only after all CI jobs pass and the AGPL-3.0 license file exists. The tag must match the backend project version. The published digest gets an SBOM, provenance, and a keyless Cosign signature. The image embeds the license and points its visible source link at the matching tag. Check the tag workflow and published digest before treating an image tag as released. See the [RC3 release notes](releases/0.2.0-rc.3.md).

Before tagging, run `scripts/test-public-compose-contract.sh`, test the [public deployment profile](deployment/public-self-hosting.md) with a disposable volume, and restore a backup. After the first publish creates the GHCR package, make it public and verify an anonymous pull before recommending it to self-hosters. Review and upgrade vulnerable dependencies instead of adding scan exceptions.

Review every pinned image digest in the three-stage `Dockerfile`, `compose.public.yml`, and the backup scripts before each release. GitHub Dependabot can propose updates for dependencies and images, but its Docker support may only update the first `FROM` in a multi-stage Dockerfile, so the Maven and runtime stages need explicit review.

## RC3 local verification — 2026-10-17

The `0.2.0-rc.3` candidate passed 143 backend tests, 73 frontend tests, the production frontend build, and desktop/mobile Playwright smoke (30 passed; 6 intentional project skips). The resolved backend inventory contained 106 components with 0 advisories; `npm audit --omit=dev` found 0 vulnerabilities. Compose/documentation/smoke contracts, the SQLite online backup/restore test, full container smoke with restored login/list, and the disposable public runtime with Caddy configuration and live named-volume backup passed. The [tag CI](https://github.com/utrost/Listful-Thinking/actions/runs/39059482487) then passed its strict image scan and multi-architecture publish/signing jobs. The public digest is `sha256:c16ef4edd27a6ff367e84ef8451d6d7a00ce10d644259939887b2a786ecbe743`; the [GitHub prerelease](https://github.com/utrost/Listful-Thinking/releases/tag/v0.2.0-rc.3) has a checksummed archive of the immutable tag.

## V18 website capture — deployed 2026-10-17

PR [#53](https://github.com/utrost/Listful-Thinking/pull/53) merged reviewed website capture, wishlist product fields, event source/location, and guest claim controls as `ea97030`. Migration V18 adds columns to `items` and `lists`. Before cutover, a stopped-container V17 backup passed integrity and foreign-key checks; the new image migrated an isolated restore to V18 with all 1 account, 1 list, and 3 items preserved. A fresh cutover backup is retained for rollback to the V17 image.

All [six PR CI jobs](https://github.com/utrost/Listful-Thinking/actions/runs/39014653891) and [six merged-main CI jobs](https://github.com/utrost/Listful-Thinking/actions/runs/39014873586) passed, including the strict dependency scan, container smoke, and desktop/mobile Playwright smoke with the capture journeys. Local verification completed on 2026-10-17: full backend suite, 73 frontend unit tests, frontend production build, documentation contract, desktop/mobile deterministic capture Playwright journeys, and a guest claim/correction/release browser journey. Four additional Playwright journeys fetched public pages without mocking extraction: MUJI product, Good Food recipe, Berliner Philharmoniker `MusicEvent`, and King Arthur recipe. Event subtype detection was corrected after the first live run, then the focused backend scraper suite and all four live journeys passed. Run the opt-in live suite from `frontend/` with `LIVE_WEB_CAPTURE=1 npx playwright test tests/gui/live-capture.spec.ts --project=chromium-desktop`; external sites may change.

Alice's live V18 database passed `integrity_check`, `foreign_key_check`, column checks, and preserved counts after cutover. The new image and frontend markers, Tailnet-only bind, UID 1000, original application settings, private database permissions, readiness, and read-only desktop/mobile Chromium login-page checks passed. See the [deployment record](deployment/alice-tailnet.md) for image IDs and private backup locations.

## Local quality gates

Run from the repository root:

```bash
cd backend && mvn test
cd ../frontend && npm test && npm run build && npm run test:gui
cd .. && scripts/test-smoke-contract.sh && scripts/test-compose-env-contract.sh && scripts/test-docs-contract.sh && python3 scripts/test-backup.py
scripts/test-public-compose-contract.sh
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
cp .env.example .env
python3 -c 'import secrets; print(secrets.token_urlsafe(48))'
# Paste the generated value into BOOTSTRAP_TOKEN in .env.
docker compose --env-file .env up --build
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

## Previous deployed main — 2026-10-16

Merged code commit `f5b6b64` previously ran on Alice; deployment-record commit `39b4d2a` records that cutover. PR #51 added desktop/mobile Playwright user and admin processes; PR #52 added account setup/recovery, password change, sharing notifications, owner transfer, and inactive-owner admin handoff. [All six CI jobs passed](https://github.com/utrost/Listful-Thinking/actions/runs/38966842242) on the final feature commit, including strict dependency scanning and GUI smoke.

The release image is `listful-thinking:handoff-20261016-f5b6b64` with ID `sha256:c387463d10361416fa3a75b334cfc4a902f2827fc6112b05bd07e4ddfd04d0ab`. V17 stayed unchanged. Consistent pre/post deployment SQLite copies passed integrity and foreign-key checks; 1 user, 1 list, and 3 items remained. Live readiness, Tailnet-only exposure, settings preservation, frontend assets, and desktop/mobile login-page loads passed. The [Alice runbook](deployment/alice-tailnet.md) holds the private backup location and records a recovered first cutover attempt.

## Security test matrix

For the full current posture and known weak points, see [Current State and Risk Register](current-state-and-risk-register.md).

Implemented automated coverage:

- Password hashes are not returned from auth or admin user APIs.
- Passwords are persisted only as salted BCrypt hashes; same plaintext passwords produce different stored hashes.
- Password reset stores a fresh salted BCrypt hash and invalidates the old plaintext match.
- First registered user needs the server setup code and is `ADMIN`; later users are `USER` when registration is enabled.
- Non-admin users cannot access `/api/v1/admin/**`.
- Registration-disabled errors are localized.
- Owner list and item APIs reject guessed IDs from other users.
- Internal sharing supports `READ` recipients and `CONTRIBUTE` recipients; contributor mutations remain item-scoped and list/share management stays owner-only.
- Public share DTOs exclude owner/admin/internal data.
- Revoked public tokens fail.
- SQL-injection-shaped login usernames and public-share tokens do not authenticate or resolve records.
- Duplicate guest claims return conflict.
- V18 guest controls require a hashed per-claim token for rename/release, and ordinary wishlist reads hide guest names. The owner can release a mistaken reservation.
- V18 typed extraction uses the guarded scraper fetch path; user-reviewed list/item writes persist only selected fields and source URLs.
- Oversized API JSON bodies return `413 payload_too_large` before controller parsing.
- Sensitive POST endpoints return `429 rate_limited` after too many requests from one client/window.
- Scraper rejects non-HTTP(S) schemes, private/local/metadata network targets, unsafe redirect targets, and caps downloaded HTML at 1 MiB.
- Browser-style authenticated mutations require `X-CSRF-TOKEN`; the SPA fetches it from `/api/v1/auth/csrf`.
- API responses include CSP, referrer policy, and permissions policy headers.
- Filter-level security rejects are written to `security_events`.
- Notifications are owner-scoped.
- Responsibility labels are preserved through item create/update/read paths and remain authorization-neutral.
- Setup/recovery links use hashed one-time tokens, expire after 24 hours, and are never logged in raw form; password changes revoke old sessions and tokens.
- Owner and inactive-owner admin transfers enforce active target and admin self-transfer boundaries, revoke public links, notify users, and audit the handoff.
- Public links expire after 56 days; Trash purges deleted lists, templates, and items after 14 days.

Known weak points and future hardening:

- Alice/private deployment is Tailnet HTTP; public internet deployment needs HTTPS/TLS termination, verified HSTS, and `SESSION_COOKIE_SECURE=true`.
- Public share token hashes are stored at rest; raw bearer tokens are returned only at generation time and legacy raw rows are migrated to hashes.
- Structured audit rows cover filter rejects and selected admin, ownership, and public-link events; full auth lifecycle coverage remains open.
- CI has OSV scanning, and RC2 included a resolved runtime SBOM; release images and JARs are not signed.
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

RC2 packaging additionally passed four focused privacy browser checks, the private-file-permission smoke and application-owned-volume restore. [All six GitHub CI jobs passed](https://github.com/utrost/Listful-Thinking/actions/runs/36269274093). The live V16 migration/data/permissions and authenticated SMTP TLS connection were verified without sending email. Trivy 0.74.0 reported no known Alpine-package or bundled-JAR vulnerabilities in the release archive.
