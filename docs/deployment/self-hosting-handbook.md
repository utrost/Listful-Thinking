# Self-Hosting Handbook

This handbook is for someone running Listful Thinking for a household, family, club, care team, or small private group.

The short version:

- **Private LAN/Tailnet use:** run the app on HTTP inside a trusted private network. This is the simplest and recommended first deployment.
- **Public internet use:** put the app behind HTTPS first. Enable secure session cookies and make the reverse proxy responsible for TLS and HSTS.
- **Never expose the Spring Boot container directly to the public internet over plain HTTP.**

For the exact currently used private Alice runbook, see [Alice Tailnet Deployment](alice-tailnet.md). This document is the general operator handbook.

## Choose your deployment posture

### Option A: Private LAN or Tailnet

Use this when every user reaches the app through a private network such as a LAN, VPN, or Tailnet.

This posture is acceptable for a small trusted instance because the app is not reachable from the public internet. You still need backups, account hygiene, and updates, but you do **not** need a public domain, public TLS certificate, or HSTS to start.

Recommended settings:

```env
PUBLIC_BASE_URL=http://your-private-host:8080
SESSION_COOKIE_SECURE=false
REGISTRATION_ENABLED=false
TRUST_FORWARDED_FOR=false
RATE_LIMIT_ENABLED=true
CSRF_ENABLED=true
SCRAPER_ALLOW_PRIVATE_ADDRESSES=false
```

Notes:

- Use `REGISTRATION_ENABLED=false` after the first admin account exists.
- Leave `TRUST_FORWARDED_FOR=false` unless the app is behind a trusted reverse proxy that strips spoofed inbound forwarding headers.
- Public guest links are still bearer links. Anyone who receives one can use it according to its mode, even on a private network.

### Option B: Public internet behind HTTPS

Use this only when users need to access the instance from outside your private network.

Required differences from private HTTP:

```env
PUBLIC_BASE_URL=https://lists.example.org
SESSION_COOKIE_SECURE=true
REGISTRATION_ENABLED=false
TRUST_FORWARDED_FOR=true
RATE_LIMIT_ENABLED=true
CSRF_ENABLED=true
SCRAPER_ALLOW_PRIVATE_ADDRESSES=false
PUBLIC_HOSTING=true
```

You must also terminate HTTPS in a reverse proxy such as Caddy, nginx, Traefik, or an equivalent front door. The example below uses Caddy because it can obtain and renew certificates automatically.

For other proxy setups, enable `TRUST_FORWARDED_FOR=true` only if all of these are true:

1. Listful Thinking is reachable only through your reverse proxy, not directly from the internet.
2. The reverse proxy overwrites or strips inbound `X-Forwarded-For` from clients.
3. You want rate-limit/audit client IPs to use the original client address instead of the proxy address.

The shipped public Compose profile meets these conditions. For a different proxy, leave it false until the header boundary has been verified.

## Local quickstart versus production profile

Use the default `docker-compose.yml` for a quick local first look:

```bash
cp .env.example .env
python3 -c 'import secrets; print(secrets.token_urlsafe(48))'
# Paste the generated value into BOOTSTRAP_TOKEN in .env.
docker compose --env-file .env up --build
```

Use the production profile for a longer-lived self-hosted instance:

```bash
cp .env.example .env
# Set BOOTSTRAP_TOKEN as above, and edit the other .env settings before starting.
mkdir -p data
sudo chown 1000:1000 data
docker compose --env-file .env -f compose.prod.yml up --build -d
```

The production profile adds a few operator defaults that the quickstart intentionally keeps simple:

- `restart: unless-stopped` so the container returns after host/container restarts.
- A healthcheck against `/api/v1/health`.
- A host-visible bind mount controlled by `LISTFUL_DATA_BIND=./data`.
- A private default port binding: `LISTFUL_BIND=127.0.0.1` and `LISTFUL_PORT=8080`.
- The same documented runtime hardening variables as the default Compose file.

Check health:

```bash
curl -fsS http://localhost:8080/api/v1/health
```

Open `http://localhost:8080`, enter the setup code from `.env` while registering the first account, and keep that account safe. The first account becomes `ADMIN`.

Stop the production profile:

```bash
docker compose --env-file .env -f compose.prod.yml down
```

Only delete the production data directory when you intentionally want to delete the database:

```bash
rm -rf data
```

## Data and backups

All mutable app data belongs under:

```text
/app/data
```

The default Compose file stores it in the named volume `listful-data`.

For simple host-visible backups, use a bind mount instead:

```bash
mkdir -p data
sudo chown 1000:1000 data
```

Then change the service volume to:

```yaml
volumes:
  - ./data:/app/data
```

Back up at least this file:

```text
./data/listful-thinking.sqlite
```

Create a consistent online snapshot from the production bind mount (Python 3 required):

```bash
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
python3 scripts/backup-db.py data/listful-thinking.sqlite "backups/listful-thinking-${stamp}.sqlite"
```

The helper opens the source read-only, uses SQLite's online backup API (including committed WAL data), checks integrity, and creates a private backup file without overwriting an existing destination. The account running it needs read access to the database and its journal/WAL files. Keep encrypted copies off the application host and choose a retention policy.

For the default named volume, stop the application before copying:

```bash
docker compose stop listful-thinking
container_id="$(docker compose ps -aq listful-thinking)"
mkdir -p backups
docker cp "$container_id:/app/data/listful-thinking.sqlite" backups/stopped-copy.sqlite
docker compose start listful-thinking
python3 scripts/backup-db.py backups/stopped-copy.sqlite backups/verified-copy.sqlite
```

Use a new backup filename each time. Never copy a running SQLite file with plain `cp` or `docker cp`.

### Test a restore without replacing production

Select a verified backup, place it in a new directory, and start a separate instance on a local port:

```bash
mkdir restore-check
cp backups/verified-copy.sqlite restore-check/listful-thinking.sqlite
sudo chown -R 1000:1000 restore-check
LISTFUL_DATA_BIND=./restore-check LISTFUL_PORT=18081 LISTFUL_BIND=127.0.0.1 \
  docker compose --env-file .env.example -p listful-restore -f compose.prod.yml up --build -d
curl -fsS http://127.0.0.1:18081/api/v1/health/ready
```

Open the restored instance, log in using an account from the backup, and verify expected lists and items. It has SMTP disabled by `.env.example`; avoid configuring outbound mail on a restore test. When finished:

```bash
docker compose --env-file .env.example -p listful-restore -f compose.prod.yml down
```

For actual recovery, stop production, retain its old data directory, install the verified restore at the production bind path with UID/GID 1000 ownership, and restart using the same production Compose profile. Do not mix an old SQLite file with leftover journal/WAL files. Restore before performing a downgrade: Flyway schema upgrades are forward-only.

The release smoke script performs an isolated restore and verifies readiness, login, and list contents. `python3 scripts/test-backup.py` also verifies a live WAL database snapshot.

## Public HTTPS example with Caddy

Use the shipped [public self-hosting profile](public-self-hosting.md) and its `compose.public.yml`, `Caddyfile.public`, `.env.public.example`, and backup/upgrade scripts. It requires `PUBLIC_BASE_URL=https://lists.example.org`, `SESSION_COOKIE_SECURE=true`, and `TRUST_FORWARDED_FOR=true` because the reverse proxy overwrites or strips inbound `X-Forwarded-For`. The app is not exposed on a host port. Caddy publishes only 80 and 443 and handles TLS, `Strict-Transport-Security`, and request-size limits. The first admin must enter the private setup code generated with `scripts/create-bootstrap-secret.sh`.

Start from the repository root. Copy and edit the public environment file; replace the placeholder image with a published versioned tag or digest. Then generate the secret and validate Compose before starting:

```bash
cp .env.public.example .env.public
# Edit LISTFUL_DOMAIN, PUBLIC_BASE_URL and LISTFUL_IMAGE in .env.public.
scripts/create-bootstrap-secret.sh
docker compose --env-file .env.public -f compose.public.yml config --quiet
docker compose --env-file .env.public -f compose.public.yml up -d
```

The following is the complete shipped Compose example. Keep [the file](../../compose.public.yml) and [its Caddyfile](../../Caddyfile.public) beside one another. The app uses a named SQLite volume, a mounted setup secret, a read-only root filesystem, and no host port; Caddy owns ports 80 and 443. Run `scripts/backup-public.sh` regularly and use `scripts/upgrade-public.sh` when changing image versions.

<!-- compose-public-example-start -->
```yaml
services:
  listful-thinking:
    image: ${LISTFUL_IMAGE:?Set LISTFUL_IMAGE to a versioned Listful Thinking image or digest}
    expose:
      - "8080"
    environment:
      PUBLIC_HOSTING: "true"
      PUBLIC_BASE_URL: ${PUBLIC_BASE_URL:?Set PUBLIC_BASE_URL to the HTTPS site origin}
      SESSION_COOKIE_SECURE: "true"
      REGISTRATION_ENABLED: ${REGISTRATION_ENABLED:-false}
      BOOTSTRAP_TOKEN_FILE: /run/secrets/bootstrap_token
      SYSTEM_LANG: ${SYSTEM_LANG:-en}
      MAIL_HOST: ${MAIL_HOST:-}
      MAIL_PORT: ${MAIL_PORT:-25}
      MAIL_USER: ${MAIL_USER:-}
      MAIL_AUTH: ${MAIL_AUTH:-false}
      MAIL_STARTTLS: ${MAIL_STARTTLS:-false}
      MAIL_FROM: ${MAIL_FROM:-}
      RATE_LIMIT_ENABLED: "true"
      RATE_LIMIT_MAX_REQUESTS: ${RATE_LIMIT_MAX_REQUESTS:-60}
      RATE_LIMIT_WINDOW_SECONDS: ${RATE_LIMIT_WINDOW_SECONDS:-60}
      RATE_LIMIT_MAX_BUCKETS: ${RATE_LIMIT_MAX_BUCKETS:-10000}
      TRUST_FORWARDED_FOR: "true"
      MAX_REQUEST_BODY_BYTES: ${MAX_REQUEST_BODY_BYTES:-5100000}
      SCRAPER_ALLOW_PRIVATE_ADDRESSES: "false"
      CSRF_ENABLED: "true"
    secrets:
      - bootstrap_token
    volumes:
      - listful-data:/app/data
    read_only: true
    tmpfs:
      # sqlite-jdbc extracts its native library into /tmp and loads it at startup.
      - /tmp:rw,exec,nosuid,size=64m
    cap_drop:
      - ALL
    security_opt:
      - no-new-privileges:true
    pids_limit: 256
    mem_limit: 1g
    restart: unless-stopped
    logging:
      options:
        max-size: "10m"
        max-file: "3"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/api/v1/health/ready | grep -q '\"status\":\"ok\"'"]
      interval: 30s
      timeout: 5s
      retries: 5
      start_period: 30s

  caddy:
    image: caddy:2.11.7-alpine@sha256:d8542f48d34a9cf4e4c11a478865229840e87e4c96ea3f439101f31a5d35f75f
    depends_on:
      listful-thinking:
        condition: service_healthy
    environment:
      LISTFUL_DOMAIN: ${LISTFUL_DOMAIN:?Set LISTFUL_DOMAIN to the public DNS name}
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile.public:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    restart: unless-stopped
    cap_drop:
      - ALL
    cap_add:
      - NET_BIND_SERVICE
    security_opt:
      - no-new-privileges:true
    pids_limit: 128
    mem_limit: 256m
    logging:
      options:
        max-size: "10m"
        max-file: "3"

secrets:
  bootstrap_token:
    file: ./secrets/bootstrap-token

volumes:
  listful-data:
  caddy-data:
  caddy-config:
```
<!-- compose-public-example-end -->

## Public HTTPS verification checklist

Do these checks before you tell people to use a public instance.

Health through HTTPS:

```bash
curl -fsS https://lists.example.org/api/v1/health
```

HSTS header:

```bash
curl -fsSI https://lists.example.org/ | grep -i '^strict-transport-security:'
```

Expected result includes something like:

```text
strict-transport-security: max-age=31536000
```

Secure session cookie after login:

```bash
curl -fsS -c /tmp/listful-cookies.txt \
  -H 'content-type: application/json' \
  -d '{"username":"YOUR_USER","password":"YOUR_PASSWORD"}' \
  https://lists.example.org/api/v1/auth/login >/tmp/listful-login.json

grep -i 'JSESSIONID' /tmp/listful-cookies.txt
```

The cookie line should be marked secure by curl. In Netscape cookie-jar format, the secure column should be `TRUE` for `JSESSIONID`.

Direct app port is not publicly reachable:

```bash
curl -fsS --max-time 5 http://lists.example.org:8080/api/v1/health
```

For a public deployment, that command should fail unless port `8080` is intentionally protected by a private firewall/VPN path.

Forwarded-header safety:

```bash
curl -fsSI -H 'X-Forwarded-For: 203.0.113.123' https://lists.example.org/
```

This should still return the normal page headers. It does **not** prove `TRUST_FORWARDED_FOR` is safe by itself. Only enable `TRUST_FORWARDED_FOR=true` after verifying your reverse proxy overwrites or strips inbound client-provided forwarding headers.

## Accounts and registration

Recommended first-run sequence:

1. Generate a setup code and start the instance.
2. Register the first admin account with that code.
3. Confirm registration is disabled in the Admin panel.
4. Create named local accounts for the people who should use the instance. You can set an initial password or issue a one-time setup link so each person chooses their own.
5. Configure SMTP only if you want email reminders, magic links, or password resets.
6. Create public guest links only for lists that are safe to share by bearer URL.

If you temporarily enable registration, disable it again after the intended users have joined.

Admin-issued setup/recovery links last 24 hours and work without SMTP: copy the displayed link once and pass it privately to the user. With a deliverable account email, the server sends the link and omits it from the response. An admin can reissue a link only for an active account; the new link invalidates earlier account links. Signed-in users can change their own password. If an owner leaves, deactivate that account before using the admin transfer action to move all of its lists to another active user. The admin sees list counts, not private titles or items; the target cannot be the acting admin.

## Mail configuration

Mail is optional. Without SMTP, username/password login and in-app notifications still work.

Configure SMTP when you want:

- Magic login links.
- Password reset links.
- Email reminders.
- Account setup/recovery links, when the target account has an email address.

Environment shape:

```env
MAIL_HOST=smtp.example.org
MAIL_PORT=587
MAIL_USER=listful@example.org
MAIL_PASS=change-me
MAIL_AUTH=true
MAIL_STARTTLS=true
MAIL_FROM=listful@example.org
```

For a relay without authentication, leave `MAIL_AUTH=false`. For port 587 submission, enable both authentication and required STARTTLS; the sender must be allowed by your provider. Connections, reads, and writes have five-second timeouts. Verify delivery using your own SMTP server before depending on email recovery.

Keep mail credentials out of logs and chat. Store production `.env` files with mode `0600` where practical.

## Updates

Before updating:

1. Read release notes or the commit summary.
2. Back up `listful-thinking.sqlite`.
3. Pull or copy the new source/image.
4. Rebuild and restart the container.
5. Re-run readiness, login, and database integrity/foreign-key checks; verify the expected Flyway version and data counts.

V17 introduces 14-day Trash retention and 56-day public-link expiry. Links active during V17 migration received 56 days from the upgrade. Back up before adopting V17; rolling back to a pre-V17 image requires its matching database snapshot. The deployed account/list handoff release added no later schema migration. Keep backup retention and encryption independent of the app's Trash policy.

V18 adds website source/product metadata, event location, and hashed guest claim-control tokens. Alice runs V18 after a verified backup and migration preflight. Before installing a V18 build on another existing instance, back up the database consistently, verify integrity and foreign keys, and apply V18 first to a restored copy. Keep the matching pre-V18 snapshot for rollback to a V17 image; an older image should not be started against an upgraded database without a tested compatibility plan.

Production-profile source checkout update:

```bash
git pull --ff-only
docker compose --env-file .env -f compose.prod.yml up --build -d
curl -fsS http://localhost:8080/api/v1/health
```

For the default development/named-volume profile, use `docker compose up --build -d` instead. Keep the same profile and data path that created your instance. For public HTTPS, set a versioned image and run `scripts/upgrade-public.sh`; it makes a verified backup first. Verify HSTS plus the secure session cookie again.

## Security baseline for small self-hosters

Keep these defaults unless you have a concrete reason to change them:

- `REGISTRATION_ENABLED=false` after bootstrap.
- `SESSION_COOKIE_SECURE=true` on HTTPS, `false` only on private HTTP.
- `RATE_LIMIT_ENABLED=true`.
- `CSRF_ENABLED=true`.
- `SCRAPER_ALLOW_PRIVATE_ADDRESSES=false`.
- `TRUST_FORWARDED_FOR=false` unless the proxy boundary is trusted and strips spoofed headers.
- Do not publish raw database backups.
- Rotate public guest links if a link was sent to the wrong person.
- Expect guest links to expire after 56 days and Trash to purge after 14 days; protect and retain backups according to your own policy.
- Keep the host OS, Docker, and reverse proxy updated.

## Troubleshooting

### The app starts but the browser shows no frontend

Check the container logs and health endpoint:

```bash
docker compose logs --tail=200 listful-thinking
curl -fsS http://localhost:8080/api/v1/health
```

### Login works on HTTP but fails on HTTPS

Check that `PUBLIC_BASE_URL` uses `https://...` and `SESSION_COOKIE_SECURE=true` for the public deployment. Then verify the browser receives `JSESSIONID` with the `Secure`, `HttpOnly`, and `SameSite=Strict` attributes.

### Public links point at localhost or a private host

Open the app using the externally reachable hostname before copying a public share link: the browser builds it from its current origin. `PUBLIC_BASE_URL` controls server-generated email links; set it to the externally reachable URL and restart the container. These settings do not make a private-only host publicly reachable.

### Rate limits show the proxy IP for all users

That is expected with `TRUST_FORWARDED_FOR=false`. If you need original client IPs, first configure the reverse proxy to strip/overwrite inbound forwarding headers, then set `TRUST_FORWARDED_FOR=true` and test carefully.

### URL metadata scraping fails for a shop

Scraping is best-effort. Some shops block server-side requests or return generic pages. Keep `SCRAPER_ALLOW_PRIVATE_ADDRESSES=false` on normal deployments; do not open private network scraping just to make one product site work.

## What this handbook does not cover

- Hosted SaaS operation.
- Multi-tenant public service operation.
- A full audited admin-superuser workflow.
- Managed backup scheduling, retention, or encryption. The repository includes a live verified snapshot helper and restore checks; schedule it on the host.

Those are separate release-readiness and operations topics. The current app is aimed at small self-hosted instances first.

## Personal data and security updates

Read the [personal-data security review](../security-review-2026-09-26.md) as a dated RC2 assessment before storing sensitive information. Runtime SQLite files use mode 0600; protect the containing volume and backup directories too. The app does not encrypt list content end to end. Host/Docker administrators and backup holders remain trusted. Use encrypted storage and a retention policy, and prefer authenticated invitations for private content. Public links expire after 56 days; copies in backups can outlast live deletion.

Dependency verification must use the resolved runtime inventory, not just declared POM dependencies:

```bash
mvn -f backend/pom.xml org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom
python3 scripts/audit-runtime.py backend/target/bom.json
```
