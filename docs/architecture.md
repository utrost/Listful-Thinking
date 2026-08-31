# Architecture

Listful Thinking is a single-container web app:

- Spring Boot 3 backend serves `/api/v1/**` and the compiled frontend.
- Vue 3 frontend is built with Vite and copied into Spring Boot static resources during Docker build.
- SQLite is the only database and lives under `/app/data`.
- The runtime container runs as non-root `1000:1000`.

## Core boundaries

- Backend owns auth, authorization, persistence, share tokens, scraping, reminders, and localized errors.
- Frontend owns interaction, type-specific forms, browser-language detection, and guest claiming UX.
- Public share DTOs must stay separate from internal DTOs.

## Build pipeline

1. Node stage builds `frontend/dist`.
2. Maven stage copies frontend artifacts into `backend/src/main/resources/static` and packages the fat JAR.
3. JRE stage runs only the packaged app with `/app/data` as writable volume.
