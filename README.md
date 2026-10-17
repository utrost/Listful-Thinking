# Listful Thinking

Listful Thinking is a small self-hosted web app for everyday lists that need just enough structure and sharing: wishlists, to-dos, groceries, chores, and event preparation.

It is meant for a household, family, club, care team, or small private group that wants shared lists without adopting a full productivity suite or sending list data to a hosted service. You run one container, store one SQLite database, create local users, and decide explicitly which lists stay private, which are shared with other registered users, and which get a public guest link.

The [user experience concept](docs/design/user-experience.md) describes the list workspace, navigation, user guidance, and browser validation.

## Release and branch status

The private Alice Tailnet instance runs merged `main` commit `ea97030` at schema **V18**. It includes 14-day recoverable Trash, 56-day public links, account and ownership handoffs, reviewed website capture, richer wishlist details, and guest reservation controls. The [Alice deployment record](docs/deployment/alice-tailnet.md) documents the verified image, migration, and database backup. Rolling back to a V17 image requires its matching pre-V18 database backup. See the [current state and risk register](docs/current-state-and-risk-register.md).

## What it does

- **Typed lists:** create `WISH`, `TODO`, `GROCERY`, `CHORE`, and `EVENT` lists with type-specific fields and validation.
- **Private by default:** users see their own lists unless another user shares a list with them.
- **Registered-user collaboration:** list owners can share read-only or contributor access with another local account. Contributors can add and update items; owners keep destructive/list-management control.
- **Account and ownership handoffs:** admins can issue one-time setup/recovery links, users can change passwords, owners can transfer lists to active users, and admins can transfer lists from inactive accounts without browsing private content.
- **Public guest sharing:** owners can create public links with modes for read-only viewing, wishlist claiming, or signup-style participation.
- **Website capture:** paste an HTTP(S) page, review product/recipe/event/how-to suggestions, then save wishes, grocery lines, dated event plans, or tasks. Wishlist entries keep exact variants, seller, availability, source, and price-check time. A possible duplicate is flagged before saving.
- **Guest reservation control:** claimants can correct their name or release a reservation from the same browser; an owner can release a mistaken claim. Wishlist owners and other guests do not see the claimant's name.
- **Grocery/shop mode:** use quantity/category fields, group by category, hide completed items, and clear completed grocery items after shopping.
- **Chores and events:** use due dates, simple recurrence for chores, skip/postpone actions, target dates for events, and owner/helper labels for coordination.
- **Notifications:** due-item reminders and sharing/ownership changes appear in-app; reminders can also be sent by email when SMTP is configured.
- **Admin bootstrap:** the first registered account becomes admin; admins can manage registration, create users with a password or setup link, activate/deactivate users, and inspect per-user list counts.

## Architecture and deployment posture

The current implementation is an MVP/post-MVP private self-hosting foundation:

- Spring Boot backend with session auth, CSRF protection, Flyway migrations, SQLite persistence, URL scraping, reminders, admin endpoints, and security hardening.
- Vue frontend served by the backend as a single web app.
- Single-container Docker build path with persistent `/app/data` volume.
- Automated smoke, unit, and Playwright coverage for auth, admin settings/users, typed lists, account setup, password change, handoffs, sharing, capture, item workflows, guest reservations, and container runtime checks.

Deployment posture: **private Tailnet/self-hosted ready** with a general [Self-Hosting Handbook](docs/deployment/self-hosting-handbook.md). Public internet deployments must use HTTPS, secure cookies, and reverse-proxy hardening as described in that handbook and tracked in [Current state and risk register](docs/current-state-and-risk-register.md).

### Save from a website

Open **Save from a website** after signing in, paste a page URL, and review every suggested field before saving. Products can become wishlist items; recipe ingredients become editable grocery lines; how-to steps become tasks; and events become dated lists with a location and optional preparation tasks. If a public page cannot be read, the page link remains in a manual draft. Line imports keep unsaved lines available for retry if a later save fails. This captures a snapshot, with no ongoing synchronization or automatic price tracking. Mobile Web Share Target integration is not yet available; paste the URL into the app.

Live Playwright checks used real [MUJI](https://www.muji.eu/products/set-of-5-planted-tree-paper-notebooks-b5-4594), [Good Food](https://www.bbcgoodfood.com/recipes/easy-pancakes), [Berliner Philharmoniker](https://www.berliner-philharmoniker.de/en/concert/calendar/57042/), and [King Arthur Baking](https://www.kingarthurbaking.com/recipes/simply-perfect-pancakes-recipe) pages. The MUJI URL redirected to a German localized page, so its suggested title and final source URL were localized; both were visible for review. See the [dated extraction evidence](docs/domain/scraping.md).

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

For consistent backups, use `python3 scripts/backup-db.py data/listful-thinking.sqlite backups/listful-thinking.sqlite` with a host bind mount. The helper uses SQLite online backup and verifies integrity; do not copy a live database with plain `cp`. For named volumes and a restore walkthrough, see the [Self-Hosting Handbook](docs/deployment/self-hosting-handbook.md). If you use a bind mount, create it so the container user can write to it:

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
MAIL_AUTH=false
MAIL_STARTTLS=false
MAIL_FROM=
RATE_LIMIT_ENABLED=true
RATE_LIMIT_MAX_REQUESTS=60
RATE_LIMIT_WINDOW_SECONDS=60
RATE_LIMIT_MAX_BUCKETS=10000
TRUST_FORWARDED_FOR=false
MAX_REQUEST_BODY_BYTES=5100000
SCRAPER_ALLOW_PRIVATE_ADDRESSES=false
CSRF_ENABLED=true
```

Common choices:

- The default Compose file forwards all variables listed above, so `docker compose up` and `docker compose config` should show the same runtime settings you intend to run.
- Set `PUBLIC_BASE_URL` to the URL users will open, especially when public share or email links should point at a Tailnet/reverse-proxy hostname.
- Keep `REGISTRATION_ENABLED=false` for a private instance after the first admin account exists; use the admin panel to create more users.
- Set `SESSION_COOKIE_SECURE=true` only when the app is served over HTTPS. For HTTPS deployments, verify a login response sets the session cookie with the `Secure` attribute before exposing the instance beyond a private network.
- Leave mail variables empty if you do not need email login/reset/reminders; in-app login and notifications still work.
- `MAX_REQUEST_BODY_BYTES` is explicitly bounded but defaults high enough for wishlist image data URLs (up to 5,000,000 characters); browser uploads are limited to 3.5 MB before encoding.

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

Run the local backend and frontend checks:

```bash
(cd backend && mvn test)
(cd frontend && npm ci && npm test && npm run build)
```

Playwright starts an isolated Docker stack with disposable SQLite data. From the repository root:

Install the Chromium browser once if Playwright has not done so: `(cd frontend && npx playwright install chromium)`.

```bash
(cd frontend && npx playwright test tests/gui/capture.spec.ts tests/gui/sharing.spec.ts)
(cd frontend && LIVE_WEB_CAPTURE=1 npx playwright test tests/gui/live-capture.spec.ts --project=chromium-desktop)
```

The first command uses deterministic fixtures for capture and guest controls. The second makes real requests to public sites and is opt-in because their content and access can change. Neither command targets Alice. See [Release verification](docs/release.md) for the full gate list.

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
- [Website extraction and scraping](docs/domain/scraping.md)
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

Privacy controls and deployment boundaries are documented in the [personal-data security review](docs/security-review-2026-09-26.md).
