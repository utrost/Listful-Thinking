# Current State and Risk Register

Last updated: 2026-09-04 on current `main`.

This document describes what exists in the repository and the Alice deployment today. It intentionally includes weak points and deferred hardening work so the current state is not over-sold.

## Deployment posture

Current production-like deployment is a private self-hosted MVP on Alice:

- Single Spring Boot + Vue container.
- SQLite database stored in `/app/data/listful-thinking.sqlite` on a persistent Docker volume.
- Runtime user is non-root `1000:1000`.
- Alice container: `listful-thinking-alice` using image `listful-thinking:alice`.
- Alice bind address is Tailnet-only: `100.123.149.120:8080->8080/tcp`.
- Latest live health check returned `{"status":"ok"}`.

This is considered acceptable for a family/private Tailnet MVP. It is not yet a public-internet deployment profile.

## Implemented product surface

Implemented and verified MVP/post-MVP features include:

- Session authentication with first-user admin bootstrap.
- Admin registration toggle, user listing, user creation, and user activation/deactivation.
- Typed lists: `WISH`, `TODO`, `GROCERY`, `CHORE`, `EVENT`.
- Owner list/item CRUD with type-specific validation.
- Internal sharing with `READ` and `CONTRIBUTE` permissions.
- Public links with explicit modes:
  - `VIEW`: read-only public view.
  - `WISH_CLAIM`: wishlist reservation; valid for `WISH` lists.
  - `SIGNUP`: guest signup/reservation; valid for non-`WISH` lists.
- List cloning without copying public tokens or internal shares.
- Item search/filter/sort/review helpers in the frontend.
- URL metadata preview and async wishlist URL enrichment.
- Due-date reminders with SMTP-or-in-app fallback and owner-scoped notifications.
- Grocery quantity/category fields and clear-completed workflow.
- Operational chore recurrence including daily, weekly, biweekly, monthly, quarterly, and annual intervals.

## Current security controls

Implemented controls:

- Passwords are stored as salted BCrypt hashes. Regression tests prove plaintext is not persisted and same plaintext produces different hashes.
- One-time magic-login and password-reset tokens are stored hashed, expire after 30 minutes, and cannot be reused.
- SQL-injection-shaped login usernames and public-share tokens are covered by regression tests; data access uses Spring Data/JPA derived queries or named parameters rather than string-built SQL.
- API body size limit: default `MAX_REQUEST_BODY_BYTES=65536`.
- Sensitive POST endpoint rate limiting: default 60 requests / 60 seconds / client+path+method window, with bounded bucket map.
- `X-Forwarded-For` is ignored by default; `TRUST_FORWARDED_FOR=true` is only for trusted reverse proxies that strip spoofed inbound headers.
- Browser-style authenticated mutations require the app CSRF token header `X-CSRF-TOKEN` when browser request metadata such as `Origin` or `Sec-Fetch-Site` is present.
- Session cookies are configured/documented as `HttpOnly` and `SameSite=Strict`; `Secure` should be enabled when served via HTTPS.
- Security headers are emitted: CSP, Referrer-Policy, Permissions-Policy, plus HSTS only on secure requests.
- Scraper accepts only HTTP(S), rejects private/local/link-local/metadata/multicast/CGNAT targets by default, connects to the validated resolved address, disables automatic redirects, revalidates redirects, applies timeouts, and caps downloaded HTML at 1 MiB.
- Filter-level rejects such as body-limit, rate-limit, and CSRF failures are recorded in `security_events` and logged as `security_event` lines.
- Public DTOs exclude password hashes, owner emails, internal user IDs, internal shares, settings, admin flags, and notification data.
- CI includes backend tests, frontend audit/test/build, and OSV dependency scanning for backend/frontend lockfiles.

## Known weak points and deferred hardening

These are known and intentionally documented:

1. **No public-internet TLS profile yet.** Alice currently serves Tailnet HTTP. Public hosting needs HTTPS/TLS termination, HSTS in real browser traffic, and `SESSION_COOKIE_SECURE=true`.
2. **Public share tokens are bearer tokens stored server-side as raw token values.** They are high-entropy and revocable, but hashing public share tokens at rest remains a future hardening step.
3. **CSRF protection is browser-metadata-aware.** Browser-style unsafe authenticated calls require a token, but non-browser clients without `Origin`/`Sec-Fetch-Site` are allowed for compatibility. This depends on `SameSite=Strict` cookies for browser protection.
4. **Audit logging is partial.** Filter-level rejects are logged and persisted. Admin changes, auth lifecycle events, public-share generation/revocation, and user management lifecycle events are not yet all captured as structured audit rows.
5. **No release signing/SBOM publication.** CI scans dependencies with OSV, but published images/JARs are not signed and no SBOM artifact is published.
6. **GitHub Actions deprecation warnings remain.** GitHub warns about Node 20/runtime deprecations for some `uses:` actions and `setup-java@v4` deprecation. This is maintenance noise, not a failing gate.
7. **`npm audit` is an external availability gate.** CI intentionally fails closed if the npm registry audit endpoint returns 503 or times out. OSV scanning is a second dependency gate, but a transient npm registry outage can still make the frontend job red without any code/documentation regression.
8. **Scraping is intentionally best-effort.** Many shops block server-side/data-center requests or return stale/generic pages. There is no browser automation, no cookie/proxy workflow, no confidence score, and no per-shop plugin architecture.
9. **Admin support access is intentionally limited.** Admins can manage users/settings and see list metadata inventory, but do not have a general audited content-superuser workflow.
10. **Backups/encryption are outside this repository.** The app uses a persistent SQLite volume; backup retention, backup encryption, and host hardening belong to the deployment environment.

## Verification evidence

Recent verified implementation gates:

- `mvn -q test` passed locally after the current hardening/public-share work.
- `npm test && npm run build && npm audit --omit=dev` passed locally.
- `scripts/smoke.sh` passed with health, non-root runtime, admin/users/settings, list clone, item/chore recurrence, grocery clear-completed, public claim/signup, and SQLite volume checks.
- Independent diff review for public-share mode work passed with no blocking security or logic issues.
- GitHub Actions run `33841132405` passed on the implementation commit before this documentation-only update.
- Current documentation commit backend and OSV jobs passed in CI. The frontend job is blocked by repeated `npm audit` 503/timeout responses from the npm registry, which also reproduced locally; this is documented above as an external fail-closed gate rather than a docs regression.

Docs-only verification for this update:

- `scripts/test-smoke-contract.sh` passed.
- Markdown link check passed for 26 Markdown files.
- `git diff --check` passed.

## Practical next hardening slices

If the app moves beyond private Tailnet use, prioritize:

1. Add an HTTPS reverse-proxy deployment profile and set `SESSION_COOKIE_SECURE=true`; verify HSTS on the live HTTPS endpoint.
2. Hash public share tokens at rest and migrate existing live tokens safely.
3. Extend structured audit logging to admin/user/auth/public-share lifecycle events.
4. Upgrade deprecated GitHub Actions and verify warnings disappear.
5. Publish SBOM and sign release images/artifacts.
6. Add a small admin/security view or CLI for reviewing `security_events` without raw DB access.
