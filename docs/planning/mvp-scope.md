# MVP Scope

## In scope

### Deployment

- Single Docker image.
- Docker Compose quickstart.
- One persistent `/app/data` volume.
- Non-root runtime user `1000:1000`.
- SQLite database.

### Authentication and administration

- First-user admin bootstrap.
- Local username/password login.
- Session cookies.
- Registration disabled by default.
- Admin users endpoint.
- Admin settings endpoint for registration toggle.

### Lists and items

- `WISH`, `TODO`, `GROCERY`, `CHORE`, and `EVENT` list types.
- Owner CRUD for lists and items.
- Type-specific frontend fields.
- Owner-only purchased status management.

### Sharing

- Internal registered-user `READ` and `CONTRIBUTE` sharing.
- Public share token generation/revocation.
- Public guest read view.
- Guest claim for open wishlist items and guest signup for open non-wishlist items when the public link mode allows it.

### Scraping

- Direct scrape utility endpoint.
- Async URL-only wishlist item enrichment.
- Jsoup with browser-like headers.
- Best-effort metadata extraction.

### Reminders

- Daily due/upcoming check.
- SMTP email if fully configured.
- In-app notification fallback.

### i18n

- English and German frontend locale files.
- Backend localized errors/messages.

## Out of scope for MVP

- OAuth/social login.
- Admin browsing of private user content.
- Complex permission levels beyond current `READ`/`CONTRIBUTE` internal shares.
- Full calendar-style recurrence engine beyond current daily/weekly/biweekly/monthly/quarterly/annual chore intervals.
- Price history or automated price tracking.
- Mobile native apps.
- PostgreSQL/MySQL support.
- Redis/background job infrastructure.
- Multi-instance clustering.
- Rich email templates.
- Internet-facing HTTPS/HSTS/secure-cookie deployment profile.
- Public-share-token hashing at rest.
- Full admin/auth/public-share lifecycle audit logging.

## MVP quality bar

- Backend tests cover authorization boundaries.
- Frontend builds and has at least smoke-level tests.
- Docker smoke script proves the core happy path.
- Documentation states any intentional simplifications.