# Personal-data security review — 2026-09-26

Scope: candidate v0.2.0-rc.1, the Java/Vue source and resolved runtime dependencies, desktop/mobile behavior, and the Alice deployment. This is a focused source/configuration review with regression testing, not a claim that every possible vulnerability has been eliminated.

## Confirmed findings and remediation in 0.2.0-rc.2

| Priority | Finding | Fix and evidence |
| --- | --- | --- |
| High | The dependency job scanned the POM without establishing coverage of inherited/transitive runtime versions. A resolved CycloneDX inventory of RC1 matched advisories for 16 of 79 components, including Spring and Tomcat. Advisory presence does not establish exploitability of every affected feature in this application. | Spring Boot 3.5.16; explicit security updates for Tomcat 10.1.60, Jackson 2.21.7 and Log4j 2.26.1; SQLite JDBC 3.53.4.0. OSV query of the resulting 77-component runtime inventory reports zero matches. CI now resolves and audits that inventory and fails on findings or scan errors. |
| Medium | Administration returned every owner's list title, description, dates and other metadata, including private/archived/trashed/template lists. | `/admin/lists` returns only per-user usage counts. Direct list/item access still requires ownership or an explicit share, even for application admins. Integration regression tests cover both boundaries. |
| Medium | The legacy raw-link migration accepted a stored `sha256:` hash as if it were a bearer token. An attacker would first need that hash; there was no anonymous hash-discovery endpoint found. | Hash-prefixed values cannot enter the legacy raw-token path. Tests verify neither reads nor claims accept hashes, legitimate links remain valid and legacy raw tokens still migrate. |
| Medium | Remote image URLs automatically contacted third-party hosts when private or public lists were viewed, exposing viewer IP/timing and enabling tracking by unique URLs. | Remote images require explicit consent for each URL. Uploaded raster images render locally. Referrer suppression remains enforced. Browser tests observe zero external image requests before consent, including public shares. |
| Medium | Deactivating and subsequently reactivating an account could revive an untouched old session or an unexpired email token. | V16 adds a persistent session revision. Deactivation increments it; sessions and email tokens snapshot/check it. A regression test verifies old sessions/tokens stay invalid after reactivation while a new password login works. |
| Medium | Scraper IPv6 checks did not reject translation/tunnel addresses that can encode private IPv4 destinations. | Restrict IPv6 scraping to native global unicast, excluding NAT64, 6to4 and Teredo. Tests include private IPv4, Tailnet/CGNAT, ULA, IPv4-mapped loopback and translation/tunnel forms. |
| Hardening | Auth/guest endpoints were outside the custom CSRF-token requirement. | Reject unsafe API calls with cross-site Fetch Metadata or an untrusted/null Origin, including login/logout/token consumption/guest claims. Existing session CSRF tokens still protect browser list mutations. |
| Hardening | Unsaved forms could survive logout in the SPA's memory. Reset tokens remained in the address bar until submission. | Clear private drafts/forms on logout and remove recovery-token query strings immediately after reading them. Regression browser journeys verify these behaviors. |
| Hardening | SQLite files had ordinary readable file permissions inside the container. SMTP TLS hostname verification was not explicitly enabled. | Runtime entrypoint uses umask 077 and restricts existing SQLite/sidecar files to 0600. Backup parent directories on Alice are private. SMTP enforces hostname verification; authenticated STARTTLS configuration is preserved. |
| Hardening | Early filter errors did not consistently pass Spring's cache-header writer. BCrypt now rejects passwords exceeding its byte limit instead of accepting truncation. | `no-store` and `noindex` are applied before filtering; cookie-only sessions have an explicit 30-minute idle timeout. Password creation/reset enforce the 72-byte BCrypt limit; overlong login attempts return a controlled failure. Missing-account login also performs a password hash comparison. |

## Controls reviewed

Owner/read/contributor checks, item/list identifier access, archive/Trash/template isolation, public-link revocation, password-reset session invalidation, token expiry/single use, parameterized database access, escaped text/product URLs, CSP/referrer headers, SSRF address validation and pinned socket destinations, request/rate limits, deployment bindings and private backup/configuration files were inspected. Existing role/lifecycle regression suites remain part of acceptance.

## Boundaries that remain

- **No end-to-end encryption:** the SQLite database, backups and application memory contain readable list content. Host administrators, anyone controlling the Docker daemon, and anyone who obtains a backup can read it. Application-admin privacy controls do not protect against a server administrator. Use trusted hosting and encrypted disks/backups for sensitive data.
- Alice remains bound only to its Tailscale IP. The browser URL uses HTTP inside the encrypted Tailnet tunnel; the session cookie cannot use `Secure` on that URL. For a public-internet deployment use HTTPS and `SESSION_COOKIE_SECURE=true`. This review does not change Tailnet access policy or install an HTTPS proxy.
- Public links are bearer access, do not expire automatically, and reveal the shared list's content until revoked. They can be forwarded or copied. Prefer authenticated invitations for personal information. Revocation cannot erase content already seen or copied by recipients.
- Trash is recoverable storage, not erasure. There is no automatic purge in this candidate, and old snapshots retain old content. Encrypted backups and a retention/deletion policy are still an operator responsibility.
- Deliberate URL imports contact the requested product site from the server. Remote images now require viewer consent. SMTP recovery/reminder delivery exposes the relevant message to the configured mail provider; reminders may include list/item names.
- Rate limits are per-client, process-local controls. They are not a distributed bot defense. Non-browser API clients without browser Origin/Fetch Metadata headers retain the documented cookie/API behavior; browser mutations require a CSRF token and cross-origin writes are rejected.

## Reproduce dependency verification

```bash
mvn -f backend/pom.xml org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom
python3 scripts/audit-runtime.py backend/target/bom.json
npm --prefix frontend audit --omit=dev
```

The advisory query sends public package names/versions only, never list content, source files, credentials or database contents. Counts are a dated observation, not a guarantee against future advisories. Primary references: [Spring's managed dependency inventory](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html), [Apache Tomcat security advisories](https://tomcat.apache.org/security-10.html), and the [OSV API](https://google.github.io/osv.dev/api/).

## Container verification

Trivy 0.74.0 scanned the packaged Docker archive using a freshly updated advisory database. It reported no known vulnerabilities for the Alpine 3.24.2 packages or bundled Java libraries. The image runs Temurin 17.0.20.1+1, matching the current published Java 17 release. A separate inspection queried all 46 Maven metadata records embedded in bundled JARs and returned no advisory matches, supplementing the resolved 77-component SBOM scan. These checks concern known published advisories at the time of review.

The restore smoke uses a fresh application-owned Docker volume rather than relying on the host runner's UID or world-writable backup files. This is verified in CI as well as locally.
