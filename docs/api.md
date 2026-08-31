# API Contract

Base path: `/api/v1`

## Health

- `GET /api/v1/health`

Response:

```json
{"status":"ok"}
```

## Auth

Auth uses stateful Spring Security sessions. Successful register/login creates an HTTP session. Frontend calls must include credentials.

### `POST /api/v1/auth/register`

Request:

```json
{"username":"uwe","email":"uwe@example.test","password":"correct horse battery staple"}
```

Responses:

- `201 Created` with authenticated user for successful registration.
- `403 Forbidden` with `registration_disabled` when non-first registration is disabled.
- `409 Conflict` with `username_taken` when username already exists.

First registered user becomes `ADMIN`. Later registered users become `USER` when registration is enabled.

### `POST /api/v1/auth/login`

Request:

```json
{"username":"uwe","password":"correct horse battery staple"}
```

Responses:

- `200 OK` with authenticated user.
- `401 Unauthorized` with `bad_credentials` for invalid username/password.

### `POST /api/v1/auth/logout`

Invalidates the current session.

Responses:

- `204 No Content`

### `GET /api/v1/auth/me`

Responses:

- `200 OK` with authenticated user.
- `401 Unauthorized` when no session is authenticated.

Authenticated user response:

```json
{"id":"uuid","username":"uwe","email":"uwe@example.test","role":"ADMIN"}
```

## Lists

- `GET /api/v1/lists`
- `POST /api/v1/lists`
- `PUT /api/v1/lists/{id}`
- `DELETE /api/v1/lists/{id}`

## Items

- `POST /api/v1/lists/{id}/items`
- `PUT /api/v1/items/{id}`
- `DELETE /api/v1/items/{id}`

## Scraper

- `POST /api/v1/utils/scrape`

## Public access

- `GET /api/v1/share/{token}`
- `POST /api/v1/share/{token}/items/{itemId}/claim`

## Admin

- `GET /api/v1/admin/users`
- `PUT /api/v1/admin/settings`
