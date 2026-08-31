# Listful Thinking

Self-hosted, multi-tenant list management for wishlists, chores, and events.

## Status

Fresh MVP scaffold: Spring Boot backend, Vue frontend, SQLite config, and single-container Docker build path.

## Quickstart

```bash
docker compose up --build
```

Then open <http://localhost:8080>.

The app uses one persistent volume mount:

- Docker Compose default: named volume `listful-data:/app/data`
- Optional bind mount for manual backups: `./data:/app/data` after creating it as UID/GID `1000:1000`

SQLite database file:

- `/app/data/listful-thinking.sqlite`

## Environment variables

Only `SYSTEM_LANG` is expected for a minimal zero-config startup, and even that has a default.

```env
SYSTEM_LANG=en
REGISTRATION_ENABLED=false
MAIL_HOST=
MAIL_PORT=
MAIL_USER=
MAIL_PASS=
```

## First admin bootstrap

The first registered user will become `ADMIN`. Public registration is disabled by default after bootstrap unless enabled by admin setting or `REGISTRATION_ENABLED=true`.

## Documentation

Start here:

- [Product vision](docs/product/vision.md)
- [Personas and actors](docs/product/personas.md)
- [Terminology](docs/product/terminology.md)
- [User stories](docs/product/user-stories.md)
- [MVP acceptance criteria](docs/product/acceptance-criteria.md)

Domain and architecture:

- [Architecture](docs/architecture.md)
- [API](docs/api.md)
- [Domain model](docs/domain/domain-model.md)
- [Permissions and sharing](docs/domain/permissions-and-sharing.md)
- [List types](docs/domain/list-types.md)
- [Security architecture](docs/architecture/security.md)
- [Architecture decisions](docs/architecture/architecture-decision-records.md)

Planning:

- [MVP roadmap](docs/mvp-roadmap.md)
- [MVP scope](docs/planning/mvp-scope.md)
- [Implementation slices](docs/planning/implementation-slices.md)
