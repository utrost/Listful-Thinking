# Current State and Risk Register

Last updated: 2026-10-17. Alice runs merged `main` code commit `ea97030` (PR #53) at schema V18. See the [verified deployment record](deployment/alice-tailnet.md).

## RC3 public self-hosting candidate

RC3 adds a first-admin setup code, public-hosting startup checks, a Caddy HTTPS Compose profile with trusted forwarding headers, resource limits, a bounded scraper, a verified live backup and upgrade path, AGPL-3.0 licensing with a source-code offer, and a strict container scan plus signed multiarch image release workflow. Alice remains a separate private Tailnet deployment. The public profile has a [dedicated operator runbook](deployment/public-self-hosting.md); verify the matching tag workflow, published digest, and GHCR visibility before installing it.

V18 adds reviewed website capture, structured event source/location, wishlist product details and guest claim controls. All six PR CI jobs passed, an isolated copy of Alice's database migrated cleanly, and the live V18 deployment passed data and browser checks. The mobile Web Share Target remains a later HTTPS/installability task.

## V18 release evidence and limits

The new capture flow was exercised through the browser with real MUJI, Good Food, Berliner Philharmoniker, and King Arthur Baking pages. The live run uncovered missing `MusicEvent` recognition; it was fixed and all four live journeys then passed. Deterministic desktop/mobile capture and guest-claim journeys also passed in isolated tests. CI ran the capture journeys as part of GUI smoke. The read-only Alice browser check confirmed deployed login-page loads on desktop and mobile; it did not add live list data. [Dated extraction notes](domain/scraping.md) record the exact pages and observed fields.

Website capture remains best-effort. MUJI redirected the tested English URL to a German localized page and the saved source URL followed that redirect. Prices and availability are snapshots, not monitored data. A mobile Web Share Target would require an installable HTTPS origin. V18 is a forward schema change; Alice's verified pre-V18 copy is retained for rollback to the previous image.

## Current release since RC2

Migration V17 gives links active at upgrade 56 days from that upgrade because their original creation dates were not recorded. New links expire 56 days after creation; every guest read and claim checks expiry. Deleted lists, templates and items remain recoverable for 14 days, then a daily cleanup permanently removes them. Backups can retain older copies and require a separate operator retention policy. V17's retention behavior remains in the V18 deployment.

Account and sharing work adds 24-hour one-time admin setup/recovery links, signed-in password change, in-app notifications for share changes, owner transfer to an active account, and audited admin transfer from an inactive owner to a different active account. Admin transfer includes archives, templates and Trash but does not expose private titles or content to the admin. The current user/admin and handoff journeys have desktop/mobile Playwright coverage.

## Planning candidate baseline

The [planning candidate](planning/release-candidate-0.2.md) added timezone-aware Today & upcoming, paused archives, recoverable deletion/Trash and personal templates. The [workspace concept](design/user-experience.md) is implemented. Real SMTP authentication and magic-link login were verified on Alice in September; the latest deployment preserved those settings.

## September review remediation

The [review](review-2026-09-26.md) records the pre-fix baseline at `7778df4`. The current work adds session rotation/reset revocation, audit redaction, shared-email recovery, shared-user UI fixes, CSRF refresh, item revisions, guarded list conversion, bounded import states, reminder catch-up/deduplication, mobile layout repairs, and verified backup/restore tooling. Migration V14 is forward-only; back up before upgrading. Older external audit logs are not rewritten by the database migration.

The previously failing full container smoke script has been repaired and includes restored login/list checks; it is now a CI job. Historical verification below describes earlier commits. See [September remediation status](review-remediation-2026-09-26.md) for that review's checks and remaining scope.

This document describes what exists in the repository and the Alice deployment today. It intentionally includes weak points and deferred hardening work so the current state is not over-sold.

## Deployment posture

Current production-like deployment is a private self-hosted MVP on Alice:

- Single Spring Boot + Vue container.
- SQLite database stored in `/app/data/listful-thinking.sqlite` on a persistent Docker volume.
- Runtime user is non-root `1000:1000`.
- Alice container: `listful-thinking-alice` using image `listful-thinking:alice`.
- Alice bind address is Tailnet-only: `100.123.149.120:8080->8080/tcp`.
- Latest live health check returned `{"status":"ok"}`.
- Deployment runbook: [Alice Tailnet Deployment](deployment/alice-tailnet.md).

Alice remains a family/private Tailnet deployment. The RC3 public-internet profile is a separate deployment path for independent self-hosters.

## Implemented product surface

The Alice V18 deployment includes:

- Session authentication with first-user admin bootstrap.
- Admin registration toggle, user listing, user creation, and user activation/deactivation.
- Admin-created accounts with one-time setup links, link reissue for account recovery without email, and signed-in password change.
- Typed lists: `WISH`, `TODO`, `GROCERY`, `CHORE`, `EVENT`.
- Owner list/item CRUD with type-specific validation.
- Internal sharing with `READ` and `CONTRIBUTE` permissions.
- In-app share/update/revocation notifications, list-owner transfer, and inactive-owner admin handoff with audit records.
- Recoverable Trash for 14 days and public guest links that expire after 56 days.
- Public links with explicit modes:
  - `VIEW`: read-only public view.
  - `WISH_CLAIM`: wishlist reservation; valid for `WISH` lists.
  - `SIGNUP`: guest signup/reservation; valid for non-`WISH` lists.
- List cloning without copying public tokens or internal shares.
- Item search/filter/sort/review helpers in the frontend.
- URL metadata preview and async wishlist URL enrichment.
- Reviewed product, recipe, event, and how-to website capture with source-linked snapshots and editable drafts.
- Wishlist variant, seller, availability, and price-check details; guest claim correction/release controls.
- Due-date reminders with SMTP-or-in-app fallback and owner-scoped notifications.
- Grocery quantity/category fields and clear-completed workflow.
- Operational chore recurrence including daily, weekly, biweekly, monthly, quarterly, and annual intervals.
- Optional item responsibility labels (`ownerLabel`, `assistantLabels`) for work-style `TODO`, `CHORE`, and `EVENT` items. These labels are coordination metadata only; list ownership and sharing remain the authorization model. The backend now rejects non-blank responsibility labels on `WISH` and `GROCERY` items, matching the frontend form contract.

## Current security controls

Implemented controls:

- Passwords are stored as salted BCrypt hashes. Regression tests prove plaintext is not persisted and same plaintext produces different hashes.
- One-time magic-login and self-service password-reset tokens are stored hashed, expire after 30 minutes, and cannot be reused. Admin-issued setup/recovery links use the reset flow with a 24-hour lifetime; issuing a new link invalidates earlier account links.
- SQL-injection-shaped login usernames and public-share tokens are covered by regression tests; data access uses Spring Data/JPA derived queries or named parameters rather than string-built SQL.
- API body size limit: default `MAX_REQUEST_BODY_BYTES=5100000`, sized for bounded wishlist image data URLs.
- Sensitive POST endpoint rate limiting: default 60 requests / 60 seconds / client+path+method window, with bounded bucket map.
- `X-Forwarded-For` is ignored by default; `TRUST_FORWARDED_FOR=true` is only for trusted reverse proxies that strip spoofed inbound headers.
- Browser-style authenticated mutations require the app CSRF token header `X-CSRF-TOKEN` when browser request metadata such as `Origin` or `Sec-Fetch-Site` is present.
- Session cookies are configured/documented as `HttpOnly` and `SameSite=Strict`; `Secure` should be enabled when served via HTTPS.
- Security headers are emitted: CSP, Referrer-Policy, Permissions-Policy, plus HSTS only on secure requests.
- Scraper accepts only HTTP(S), rejects private/local/link-local/metadata/multicast/CGNAT targets by default, connects to the validated resolved address, disables automatic redirects, revalidates redirects, applies timeouts, and caps downloaded HTML at 1 MiB.
- Filter-level rejects such as body-limit, rate-limit, and CSRF failures are recorded in `security_events` and logged as `security_event` lines.
- Structured audit rows are recorded for admin user creation/setup-link issuance, activation/deactivation, registration-setting changes, list ownership handoffs, and public-share creation/revocation. Audit details intentionally avoid passwords and raw setup/public-share tokens.
- Public DTOs exclude password hashes, owner emails, internal user IDs, internal shares, settings, admin flags, and notification data.
- CI includes backend tests, frontend audit/test/build, and OSV dependency scanning for backend/frontend lockfiles.

## Known weak points and deferred hardening

These are known and intentionally documented:

1. **Public profile has not yet been validated across independent hosts.** Alice currently serves Tailnet HTTP. The new public profile needs a real-domain certificate, secure-cookie, restore, and update check on each self-hoster's host.
2. **CSRF protection is browser-metadata-aware.** Browser-style unsafe authenticated calls require a token, but non-browser clients without `Origin`/`Sec-Fetch-Site` are allowed for compatibility. This depends on `SameSite=Strict` cookies for browser protection.
3. **Audit logging is still partial.** Filter-level rejects, selected admin/user-management changes, ownership handoffs, and public-share generation/revocation are logged and persisted as structured rows. Auth lifecycle events such as login success/failure, logout, magic-link use, and password-reset use are not yet all captured as structured audit rows.
4. **Public image signatures require per-release verification.** The tag workflow adds SBOM, provenance, and keyless image signing after a strict container scan; confirm the published digest and signature before recommending a tag.
5. **`npm audit` is an external availability gate.** CI intentionally fails closed if the npm registry audit endpoint returns 503 or times out. OSV scanning is a second dependency gate, but a transient npm registry outage can still make the frontend job red without any code/documentation regression.
6. **Scraping is intentionally best-effort.** Many shops block server-side/data-center requests or return stale/generic pages. There is no browser automation, no cookie/proxy workflow, no confidence score, and no per-shop plugin architecture.
7. **Responsibility labels are not structured actors yet.** `ownerLabel` and `assistantLabels` are free-text metadata. Structured members, assistant agents, notification routing, rotations, and permissions remain future slices.
8. **Admin support access is intentionally limited.** Admins can manage users/settings and see per-user list counts, but do not have a general audited content-superuser workflow.
9. **Backup scheduling/encryption are operator responsibilities.** The repository now provides a consistent SQLite snapshot helper and restore verification; retention, encryption, and host hardening remain deployment responsibilities.

## Verification evidence

Latest verified gates:

- [All six PR CI jobs](https://github.com/utrost/Listful-Thinking/actions/runs/39014653891) and [all six merged-main CI jobs](https://github.com/utrost/Listful-Thinking/actions/runs/39014873586) passed, including backend, frontend, contracts, container smoke, strict dependency scan, and desktop/mobile Playwright smoke with capture journeys.
- Alice runs image `sha256:224ce4e3ec395f78ba8947e67810300f0fa469090fce30d43f09de3fca7d0106`. Live readiness, Tailnet-only binding, UID 1000, preserved application settings, V18 frontend asset, and desktop/mobile Chromium login pages passed. Pre/post deployment SQLite copies passed integrity and foreign-key checks; isolated and live migrations preserved 1 user, 1 list, and 3 items and verified V18 columns.

Earlier implementation evidence (historical):

- GitHub Actions run `35335975768` passed on merged `main` commit `28349f7` for the responsibility-label list-type contract fix.
- GitHub Actions run `35258643915` passed on merged `main` commit `f8920ab`, and GitHub Actions Node 20/runtime deprecation warnings were removed by upgrading official action majors.
- Focused backend audit tests passed locally with `mvn -q -Dtest=AdminUsersControllerTests,AdminSettingsControllerTests,PublicShareTests test`.
- GitHub Actions run `33962767179` passed on merged `main` commit `5a8c1f1` for the item responsibility implementation.
- Alice deployment of `5a8c1f1` was verified with health, browser load, packaged asset markers, Flyway version `12`, `owner_label`/`assistant_labels` columns, item-count preservation, and Tailnet-only binding.
- `mvn -q test` passed locally after the September hardening/public-share work.
- `npm test && npm run build && npm audit --omit=dev` passed locally.
- `scripts/smoke.sh` passed with health, non-root runtime, admin/users/settings, list clone, item/chore recurrence, grocery clear-completed, public claim/signup, and SQLite volume checks.
- Independent diff review for public-share mode work passed with no blocking security or logic issues.
- GitHub Actions run `33841132405` passed on the implementation commit before this documentation-only update.
- At that earlier documentation-only commit, backend and OSV jobs passed while `npm audit` received registry 503/timeout responses. The latest six-job run above passed, including the strict dependency scan.

Earlier docs-only verification:

- `scripts/test-smoke-contract.sh` passed.
- Markdown link check passed for 26 Markdown files.
- `git diff --check` passed.

## Practical next hardening slices

If the app moves beyond private Tailnet use, prioritize:

1. Add an HTTPS reverse-proxy deployment profile and set `SESSION_COOKIE_SECURE=true`; verify HSTS on the live HTTPS endpoint.
2. Extend structured audit logging to the remaining auth lifecycle events.
3. Sign release images/artifacts and continue publishing the resolved runtime SBOM.
4. Add a small admin/security view or CLI for reviewing `security_events` without raw DB access.
