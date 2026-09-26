# Review remediation — 2026-09-26

This change set addresses the fourteen prioritized findings in [the original review](review-2026-09-26.md), keeping the existing Docker/Spring Boot/Vue architecture. The initial verification below was performed before deployment. The fixes were subsequently deployed, real SMTP recovery was verified, and the workspace was redesigned; see the current deployment record and planning candidate.

## Implemented

| Finding | Change |
| --- | --- |
| 1. Session fixation | Rotate the session ID on registration/login/magic login and clear the prior CSRF token. |
| 2. Recovery leaves access valid | Password resets revoke account tokens; credential snapshots invalidate old sessions on their next request. |
| 3. Audit bearer leakage | Redact public bearer paths before persistence/logging; V14 redacts historical database audit paths. |
| 4. Shared-user startup | Fetch sharing rosters only for owners and keep detail errors separate from authentication state. |
| 5. Unsafe backup guidance | Add verified SQLite online snapshots, WAL backup tests, and isolated container restore/login/list checks. |
| 6. CSRF cache lifecycle | Clear cached tokens at auth boundaries; refresh/retry a recognized CSRF rejection once. |
| 7. Duplicate family emails | Support username-qualified recovery and neutral responses for ambiguous email-only requests. |
| 8. Broken release smoke | Configure its body-size limit explicitly, execute the full smoke in CI, and include docs/backup checks. |
| 9. Mobile overflow/daily UX | Collapse and extract administration, reduce the header, wrap long content, and prevent stretched list cards. |
| 10. One-time public URLs | Explain active links, add copy/open controls, and confirm replacement of existing links. |
| 11. Invalid type conversion | Reject changes of type for populated lists, including through the API. |
| 12. Stale overwrites | Require item revisions for full edits, reject stale writes, and increment revisions for guest claims. |
| 13. Stuck metadata imports | Persist import state, provide fallback names/retry, poll with a deadline, bound workers/queue, recover interrupted jobs, and reject outdated results. |
| 14. Missed/duplicate reminders | Scan every minute with overdue catch-up; persist a unique occurrence key and inbox fallback before email delivery. |

Additional changes include item-delete/clear confirmations, mutation pending states, error focus, translated statuses/list types, formatted dates and currencies (including zero prices), notes for every list type, database readiness, SMTP AUTH/required STARTTLS/sender/timeouts, and a separate anonymous browser context for public guest tests.

## Upgrade implications

- Back up before upgrading. Flyway V14 adds item revision/currency/import state and notification delivery keys; rollback requires restoring the prior database and application together.
- Custom clients must send `If-Match: "<version>"` on item PUT, using the version returned by the API. Missing or stale revisions return HTTP 409. Reload and reconcile instead of blindly replaying an edit.
- Existing prices are migrated as EUR, preserving the old display convention. Review prices originally imported from non-EUR shops; their original currency cannot be reconstructed automatically.
- Database audit history is redacted. External archived logs are outside the application migration: review retention and rotate links known to have been recorded there.
- Reminders favor a durable inbox fallback and at most one automatic SMTP attempt per occurrence. There is no cross-system atomic transaction or automatic mail retry queue.

## Verification

- Backend: full suite passed with 105 tests; the final same-day rescheduling fix passed all 7 reminder tests, bringing the verified distinct total to 106. No failures/errors/skips.
- Frontend: 60 tests across 11 files passed; production type checking/build passed.
- Browser: all 6 active Chromium desktop/mobile scenarios passed; 6 duplicate project combinations were deliberately skipped.
- Full Docker smoke passed, including a separate restored container with verified readiness, login, and list contents. The later reminder-only edge-case adjustment was verified with the focused reminder suite.
- Smoke/Compose/docs contracts, Markdown links, online WAL backup/restore checks, and `git diff --check` passed.
- Upgrade check: a snapshot of the pre-fix review database migrated from V13 to V14, preserved both test users and both test items, and redacted the historical bearer-path audit record.
- Manual browser measurement: a 390 px viewport now has a 390 px document width. Browser regressions additionally check expanded administration and long list titles.

Historical initial verification only: later deployment and real SMTP checks are recorded in [Alice deployment](deployment/alice-tailnet.md).

## Remaining broader work

These are deliberately separate from the fourteen prioritized repairs:

- Moving image data out of item JSON, generating thumbnails, and paginating large lists requires a separate storage/API change and migration. Current per-image/body limits remain; large image-heavy lists can still be expensive.
- The custom CSRF policy still permits API requests without browser metadata and exempts auth/public routes. A complete replacement with framework CSRF plus explicit non-cookie API authentication remains an architectural follow-up; it is not claimed fixed by refreshing the frontend token.
- Full screen-reader/WCAG and cross-browser audits, load testing, and a comprehensive dependency advisory review remain separate verification work. The current tests cover the identified Chromium mobile/workflow failures.
- Auth lifecycle audit expansion, audit retention, and per-account abuse controls remain useful hardening work. This patch does redact bearer paths and bound/rate-limit import work.
- Administration has been extracted from `App.vue`; further decomposition can follow feature work. The remainder still contains several workflows.
- SMTP transport configuration is now explicit and bounded, but real submission-server authentication and both recovery emails were subsequently verified; the user confirmed magic-link login.
