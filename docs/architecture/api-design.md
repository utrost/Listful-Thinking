# API Design

This document defines API design rules beyond the endpoint list in `../api.md`.

## Base path

All backend JSON API endpoints live under:

- `/api/v1`

The SPA uses browser routes outside `/api/v1`, for example `/s/:token`.

## Response style

- Return DTOs, not JPA entities.
- Use ISO-8601 timestamp strings.
- Use UUID strings for IDs.
- Use enum values as uppercase strings, for example list types (`WISH`, `TODO`, `GROCERY`, `CHORE`, `EVENT`), item statuses (`OPEN`, `CLAIMED`, `PURCHASED`, `DONE`), internal share permissions (`READ`, `CONTRIBUTE`), and public share modes (`VIEW`, `WISH_CLAIM`, `SIGNUP`).

## Error style

Recommended error shape:

```json
{
  "code": "list.notFound",
  "message": "List not found"
}
```

Rules:

- `code` is stable and English-like.
- `message` is localized through `Accept-Language` and `SYSTEM_LANG` fallback.
- Validation errors should include field-specific details when useful.

## HTTP status rules

- `200 OK`: successful reads/updates.
- `201 Created`: successful creates.
- `204 No Content`: successful deletes.
- `400 Bad Request`: malformed input, invalid URL scheme, validation failure.
- `401 Unauthorized`: authentication required.
- `403 Forbidden`: authenticated user lacks permission and existence disclosure is acceptable.
- `404 Not Found`: missing or private resource should not be discoverable.
- `409 Conflict`: duplicate guest claim/signup or invalid state transition.

## Auth and account endpoints

- `POST /api/v1/auth/register`
- `GET /api/v1/auth/settings`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `GET /api/v1/auth/csrf`
- `POST /api/v1/auth/magic-link` and `POST /api/v1/auth/magic-link/consume`
- `POST /api/v1/auth/password-reset` and `POST /api/v1/auth/password-reset/consume`
- `POST /api/v1/auth/change-password`

Admins use `POST /api/v1/admin/users/with-setup` or `/admin/users/{id}/setup-link` to issue one-time account setup/recovery links. An active owner uses `POST /api/v1/lists/{id}/transfer`; an admin uses `POST /api/v1/admin/users/{id}/transfer-lists` only after deactivating the old owner. See [the endpoint contract](../api.md) for bodies, responses, and authorization.

## Public endpoints

Public guest endpoints are unauthenticated but still constrained by token context:

- `GET /api/v1/share/{token}`
- `POST /api/v1/share/{token}/items/{itemId}/claim`

## Versioning

MVP uses `/api/v1`. Avoid breaking response shapes inside v1 after release unless explicitly documented.
