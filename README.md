# Listful Thinking

Listful Thinking is a small self-hosted web app for everyday lists that need just enough structure and sharing: wishlists, to-dos, groceries, chores, and event preparation.

It is meant for a household, family, club, care team, or small private group that wants shared lists without adopting a full productivity suite or sending list data to a hosted service. You run one container, store one SQLite database, create local users, and decide explicitly which lists stay private, which are shared with other registered users, and which get a public guest link.

## What it does

- **Typed lists:** create `WISH`, `TODO`, `GROCERY`, `CHORE`, and `EVENT` lists with type-specific fields and validation.
- **Private by default:** users see their own lists unless another user shares a list with them.
- **Registered-user collaboration:** list owners can share read-only or contributor access with another local account. Contributors can add and update items; owners keep destructive/list-management control.
- **Public guest sharing:** owners can create public links with modes for read-only viewing, wishlist claiming, or signup-style participation.
- **Wishlist helpers:** paste product URLs, scrape basic metadata where allowed, track claim/purchase state, and let guests reserve gifts.
- **Grocery/shop mode:** use quantity/category fields, group by category, hide completed items, and clear completed grocery items after shopping.
- **Chores and events:** use due dates, simple recurrence for chores, skip/postpone actions, target dates for events, and owner/helper labels for coordination.
- **Notifications:** reminders appear in-app and can also be sent by email when SMTP is configured.
- **Admin bootstrap:** the first registered account becomes admin; admins can manage registration, create users, activate/deactivate users, and inspect list ownership metadata.

## Current status

The current implementation is an MVP/post-MVP private self-hosting foundation:

- Spring Boot backend with session auth, CSRF protection, Flyway migrations, SQLite persistence, URL scraping, reminders, admin endpoints, and security hardening.
- Vue frontend served by the backend as a single web app.
- Single-container Docker build path with persistent `/app/data` volume.
- Automated smoke coverage for auth, admin settings/users, typed lists, internal sharing, item workflows, public links, guest claiming/signup, responsibility labels, and container runtime checks.

Deployment posture: **private Tailnet/self-hosted ready** with a general [Self-Hosting Handbook](docs/deployment/self-hosting-handbook.md). Public internet deployments must use HTTPS, secure cookies, and reverse-proxy hardening as described in that handbook and tracked in [Current state and risk register](docs/current-state-and-risk-register.md).

## Requirements

For the recommended container path:

- Docker with Docker Compose v2
- `curl` and `python3` if you want to run the smoke script

For local development without Docker:

- Java 17
- Maven 3.9+
- Node.js/npm compatible with the frontend toolchain (`package-lock.json` is committed; the Docker build currently uses Node 24)

## Quick start with Docker Compose

Use this for a local evaluation or development-style first look:

```bash
docker compose up --build
```

Then open:

```text
http://localhost:8080
```

On a fresh database, register the first account in the browser. That first account becomes `ADMIN`. Later self-registration is disabled by default unless you enable it through admin settings or start with `REGISTRATION_ENABLED=true`.

Stop the app with:

```bash
docker compose down
```

Remove the app container and the development SQLite volume only when you deliberately want to delete local app data:

```bash
docker compose down -v
```

## Production self-hosting Compose profile

For a longer-lived self-hosted instance, use the production Compose profile and explicit environment file:

```bash
cp .env.example .env
# edit .env: PUBLIC_BASE_URL, LISTFUL_BIND/LISTFUL_PORT, mail if needed
mkdir -p data
sudo chown 1000:1000 data
docker compose --env-file .env -f compose.prod.yml up --build -d
curl -fsS http://localhost:8080/api/v1/health
```

The production profile differs from the quickstart in a few important ways:

- It uses a host-visible data bind mount controlled by `LISTFUL_DATA_BIND` instead of an anonymous-looking named volume.
- It sets `restart: unless-stopped` and a container healthcheck.
- It binds to `127.0.0.1:8080` by default, so a same-host reverse proxy can reach the app while the app port is not directly public.
- It keeps public-internet behavior opt-in: set `PUBLIC_BASE_URL=https://...` and `SESSION_COOKIE_SECURE=true` only when HTTPS is actually terminating in front of the app.

For the full operator guide, including private LAN/Tailnet versus public HTTPS deployment choices, see [Self-Hosting Handbook](docs/deployment/self-hosting-handbook.md).

## Data and backups

The container stores its database at:

```text
/app/data/listful-thinking.sqlite
```

The default Compose file mounts that path as the named volume:

```text
listful-data:/app/data
```

For simple manual backups, copy the SQLite file from the volume or bind-mount `./data:/app/data`. If you use a bind mount, create it so the container user can write to it:

```bash
mkdir -p data
sudo chown 1000:1000 data
```

Then change the Compose volume entry to:

```yaml
volumes:
  - ./data:/app/data
```

## Configuration

The app is designed to start with no required external services. Useful environment variables are:

```env
SYSTEM_LANG=en
REGISTRATION_ENABLED=false
PUBLIC_BASE_URL=http://localhost:8080
SESSION_COOKIE_SECURE=false
MAIL_HOST=
MAIL_PORT=25
MAIL_USER=
MAIL_PASS=
RATE_LIMIT_ENABLED=true
RATE_LIMIT_MAX_REQUESTS=60
RATE_LIMIT_WINDOW_SECONDS=60
RATE_LIMIT_MAX_BUCKETS=10000
TRUST_FORWARDED_FOR=false
MAX_REQUEST_BODY_BYTES=65536
SCRAPER_ALLOW_PRIVATE_ADDRESSES=false
CSRF_ENABLED=true
```

Common choices:

- The default Compose file forwards all variables listed above, so `docker compose up` and `docker compose config` should show the same runtime settings you intend to run.
- Set `PUBLIC_BASE_URL` to the URL users will open, especially when public share or email links should point at a Tailnet/reverse-proxy hostname.
- Keep `REGISTRATION_ENABLED=false` for a private instance after the first admin account exists; use the admin panel to create more users.
- Set `SESSION_COOKIE_SECURE=true` only when the app is served over HTTPS. For HTTPS deployments, verify a login response sets the session cookie with the `Secure` attribute before exposing the instance beyond a private network.
- Leave mail variables empty if you do not need email login/reset/reminders; in-app login and notifications still work.

## Build and run manually for development

The Docker build performs these steps automatically. For local development, run them explicitly.

Build the frontend:

```bash
cd frontend
npm ci
npm run build
cd ..
```

Copy the built frontend into the backend static resources:

```bash
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -R frontend/dist/. backend/src/main/resources/static/
```

Run backend tests and package the application:

```bash
cd backend
mvn test
mvn package
cd ..
```

Run the packaged app with a local SQLite database path:

```bash
mkdir -p data
LISTFUL_DB_PATH="$PWD/data/listful-thinking.sqlite" \
PUBLIC_BASE_URL="http://localhost:8080" \
java -jar backend/target/listful-thinking-*.jar
```

Then open <http://localhost:8080>.

For frontend-only development, you can run Vite:

```bash
cd frontend
npm ci
npm run dev
```

The production-style path is still the integrated Spring Boot app that serves the built frontend and `/api/v1` backend from the same origin.

## Verify a build

Run the full container smoke test:

```bash
scripts/smoke.sh
```

The smoke script builds a temporary Docker Compose stack on port `18080`, waits for `/api/v1/health`, verifies the non-root container user and SQLite volume, then exercises:

- first-admin bootstrap and regular user registration
- admin settings and user management
- typed list creation and validation
- internal list sharing with contributor access
- item creation/update/status workflows
- list cloning
- public share modes
- wishlist guest claiming and non-wishlist signup
- reminder-related fields and responsibility labels
- security hardening checks such as request-size and rate-limit behavior

To keep the smoke stack around for debugging after a failure:

```bash
LISTFUL_KEEP_SMOKE=true scripts/smoke.sh
```

To change the temporary smoke port:

```bash
LISTFUL_SMOKE_PORT=18081 scripts/smoke.sh
```

## Repository layout

```text
backend/     Spring Boot API, auth, persistence, Flyway migrations, tests
frontend/    Vue/Vite app, type-specific list UI, frontend tests
scripts/     Container smoke tests and script contract checks
docs/        Product, user/admin, domain, API, architecture, deployment, and planning docs
Dockerfile   Multi-stage frontend + backend + JRE image build
docker-compose.yml Local single-container runtime with a persistent SQLite volume
```

## Documentation

Start here:

- [User and admin guide](docs/user-guide.md)
- [Current state and risk register](docs/current-state-and-risk-register.md)
- [Product vision](docs/product/vision.md)
- [Personas and actors](docs/product/personas.md)
- [Terminology](docs/product/terminology.md)
- [Use cases and story backlog](docs/product/use-cases.md)
- [User stories](docs/product/user-stories.md)
- [MVP acceptance criteria](docs/product/acceptance-criteria.md)

Domain and architecture:

- [Architecture](docs/architecture.md)
- [API](docs/api.md)
- [Domain model](docs/domain/domain-model.md)
- [Item responsibility metadata](docs/domain/item-responsibility.md)
- [Permissions and sharing](docs/domain/permissions-and-sharing.md)
- [List types](docs/domain/list-types.md)
- [Scraping](docs/domain/scraping.md)
- [Reminders and notifications](docs/domain/reminders.md)
- [Security architecture](docs/architecture/security.md)
- [Architecture decisions](docs/architecture/architecture-decision-records.md)
- [Release verification](docs/release.md)
- [Self-Hosting Handbook](docs/deployment/self-hosting-handbook.md)
- [Alice Tailnet deployment](docs/deployment/alice-tailnet.md)

Planning:

- [MVP roadmap](docs/mvp-roadmap.md)
- [MVP scope](docs/planning/mvp-scope.md)
- [Implementation slices](docs/planning/implementation-slices.md)
