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

All list endpoints require an authenticated registered user. MVP list access is owner-only.

### `GET /api/v1/lists`

Returns lists owned by the authenticated user only.

### `POST /api/v1/lists`

Request:

```json
{"title":"Birthday","description":"Gift ideas","type":"WISH","targetDate":null}
```

Response: `201 Created` with list.

### `GET /api/v1/lists/{id}`

Returns owned list, or `404` when the list does not exist or belongs to another user.

### `PUT /api/v1/lists/{id}`

Updates owned list metadata, or returns `404` when not owned.

### `DELETE /api/v1/lists/{id}`

Deletes owned list, or returns `404` when not owned.

List response:

```json
{"id":"uuid","title":"Birthday","description":"Gift ideas","type":"WISH","publicList":false,"shareToken":null,"targetDate":null,"createdAt":"2026-08-31T17:00:00Z"}
```

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

### `GET /api/v1/admin/settings`

Requires `ADMIN` role.

Response:

```json
{"registrationEnabled":false}
```

### `PUT /api/v1/admin/settings`

Requires `ADMIN` role.

Request:

```json
{"registrationEnabled":true}
```

Response:

```json
{"registrationEnabled":true}
```

### `GET /api/v1/admin/users`

Planned; not implemented yet.
