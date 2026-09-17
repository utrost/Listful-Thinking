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
TRUST_FORWARDED_FOR=false
RATE_LIMIT_ENABLED=true
CSRF_ENABLED=true
SCRAPER_ALLOW_PRIVATE_ADDRESSES=false
```

You must also terminate HTTPS in a reverse proxy such as Caddy, nginx, Traefik, or an equivalent front door. The example below uses Caddy because it can obtain and renew certificates automatically.

`TRUST_FORWARDED_FOR=true` is **not** normally required. Enable it only if all of these are true:

1. Listful Thinking is reachable only through your reverse proxy, not directly from the internet.
2. The reverse proxy overwrites or strips inbound `X-Forwarded-For` from clients.
3. You want rate-limit/audit client IPs to use the original client address instead of the proxy address.

If you are unsure, leave `TRUST_FORWARDED_FOR=false`. That is safer than trusting spoofable client-provided headers.

## Local quickstart versus production profile

Use the default `docker-compose.yml` for a quick local first look:

```bash
docker compose up --build
```

Use the production profile for a longer-lived self-hosted instance:

```bash
cp .env.example .env
# edit .env before starting
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

Open `http://localhost:8080`, register the first account, and keep that account safe. The first account becomes `ADMIN`.

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

Suggested manual backup before an update:

```bash
mkdir -p backups
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
cp data/listful-thinking.sqlite "backups/listful-thinking-${stamp}.sqlite"
```

If you use the named Docker volume instead of `./data`, copy the file out of a stopped or running container before updating.

## Public HTTPS example with Caddy

This is a public-internet example. Replace `lists.example.org` with your real domain.

Prerequisites:

- The DNS `A`/`AAAA` record for `lists.example.org` points to your host.
- Ports `80` and `443` reach Caddy from the internet.
- The Listful Thinking app port is not exposed directly to the public internet.

Use this shape as `compose.public.yml`:

```yaml
services:
  listful-thinking:
    build: .
    image: listful-thinking:prod
    environment:
      SYSTEM_LANG: ${SYSTEM_LANG:-en}
      REGISTRATION_ENABLED: ${REGISTRATION_ENABLED:-false}
      PUBLIC_BASE_URL: ${PUBLIC_BASE_URL:?set PUBLIC_BASE_URL}
      SESSION_COOKIE_SECURE: ${SESSION_COOKIE_SECURE:-true}
      MAIL_HOST: ${MAIL_HOST:-}
      MAIL_PORT: ${MAIL_PORT:-25}
      MAIL_USER: ${MAIL_USER:-}
      MAIL_PASS: ${MAIL_PASS:-}
      RATE_LIMIT_ENABLED: ${RATE_LIMIT_ENABLED:-true}
      RATE_LIMIT_MAX_REQUESTS: ${RATE_LIMIT_MAX_REQUESTS:-60}
      RATE_LIMIT_WINDOW_SECONDS: ${RATE_LIMIT_WINDOW_SECONDS:-60}
      RATE_LIMIT_MAX_BUCKETS: ${RATE_LIMIT_MAX_BUCKETS:-10000}
      TRUST_FORWARDED_FOR: ${TRUST_FORWARDED_FOR:-false}
      MAX_REQUEST_BODY_BYTES: ${MAX_REQUEST_BODY_BYTES:-65536}
      SCRAPER_ALLOW_PRIVATE_ADDRESSES: ${SCRAPER_ALLOW_PRIVATE_ADDRESSES:-false}
      CSRF_ENABLED: ${CSRF_ENABLED:-true}
    expose:
      - "8080"
    volumes:
      - listful-data:/app/data
    restart: unless-stopped

  caddy:
    image: caddy:2
    depends_on:
      - listful-thinking
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    restart: unless-stopped

volumes:
  listful-data:
  caddy-data:
  caddy-config:
```

Create `Caddyfile`:

```caddyfile
lists.example.org {
  encode zstd gzip

  header {
    Strict-Transport-Security "max-age=31536000; includeSubDomains"
    X-Content-Type-Options "nosniff"
  }

  reverse_proxy listful-thinking:8080 {
    header_up X-Forwarded-Proto {scheme}
    header_up X-Forwarded-Host {host}
    header_up X-Forwarded-For {remote_host}
  }
}
```

Create `.env.public`:

```env
PUBLIC_BASE_URL=https://lists.example.org
SESSION_COOKIE_SECURE=true
REGISTRATION_ENABLED=false
TRUST_FORWARDED_FOR=false
RATE_LIMIT_ENABLED=true
CSRF_ENABLED=true
SCRAPER_ALLOW_PRIVATE_ADDRESSES=false
```

Start it:

```bash
docker compose --env-file .env.public -f compose.public.yml up --build -d
```

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
strict-transport-security: max-age=31536000; includeSubDomains
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

1. Start the instance privately.
2. Register the first admin account.
3. Confirm registration is disabled in the Admin panel.
4. Create named local accounts for the people who should use the instance.
5. Configure SMTP only if you want email reminders, magic links, or password resets.
6. Create public guest links only for lists that are safe to share by bearer URL.

If you temporarily enable registration, disable it again after the intended users have joined.

## Mail configuration

Mail is optional. Without SMTP, username/password login and in-app notifications still work.

Configure SMTP when you want:

- Magic login links.
- Password reset links.
- Email reminders.

Environment shape:

```env
MAIL_HOST=smtp.example.org
MAIL_PORT=587
MAIL_USER=listful@example.org
MAIL_PASS=change-me
```

Keep mail credentials out of logs and chat. Store production `.env` files with mode `0600` where practical.

## Updates

Before updating:

1. Read release notes or the commit summary.
2. Back up `listful-thinking.sqlite`.
3. Pull or copy the new source/image.
4. Rebuild and restart the container.
5. Re-run the health and login checks.

Typical source checkout update:

```bash
git pull --ff-only
docker compose up --build -d
curl -fsS http://localhost:8080/api/v1/health
```

For public HTTPS, use the public compose command and verify HSTS plus the secure session cookie again.

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

Set `PUBLIC_BASE_URL` to the URL users should actually open and restart the container.

### Rate limits show the proxy IP for all users

That is expected with `TRUST_FORWARDED_FOR=false`. If you need original client IPs, first configure the reverse proxy to strip/overwrite inbound forwarding headers, then set `TRUST_FORWARDED_FOR=true` and test carefully.

### URL metadata scraping fails for a shop

Scraping is best-effort. Some shops block server-side requests or return generic pages. Keep `SCRAPER_ALLOW_PRIVATE_ADDRESSES=false` on normal deployments; do not open private network scraping just to make one product site work.

## What this handbook does not cover

- Hosted SaaS operation.
- Multi-tenant public service operation.
- Image signing/SBOM publication.
- A full audited admin-superuser workflow.
- Automated backups or backup encryption.

Those are separate release-readiness and operations topics. The current app is aimed at small self-hosted instances first.
