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

All list endpoints require an authenticated registered user. `GET /lists/{id}` allows the owner or an internally shared read-only user. Mutating list endpoints remain owner-only.

### `GET /api/v1/lists`

Returns lists owned by the authenticated user only.

### `POST /api/v1/lists`

Request:

```json
{"title":"Birthday","description":"Gift ideas","type":"WISH","targetDate":null}
```

Response: `201 Created` with list.

Type validation:

- `EVENT` lists require `targetDate`.
- `WISH` and `CHORE` lists reject `targetDate`; due dates live on their items.
- Type validation failures return `400 Bad Request` with `validation_failed`.

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

All item endpoints require authenticated access to the parent list. Read access allows the owner or an internally shared read-only user. Item create/update/delete remain owner-only.

### `GET /api/v1/lists/{id}/items`

Returns items for an owned list, or `404` when the list is not owned.

### `POST /api/v1/lists/{id}/items`

Creates an item on an owned list.

Request:

```json
{"name":"Camera strap","description":"Leather strap","url":"https://example.test/strap","imageUrl":null,"price":29.90,"status":"OPEN","dueDate":null,"recurrenceRule":null}
```

Response: `201 Created` with item.

URL-only wishlist creation:

```json
{"url":"https://example.test/product"}
```

For `WISH` lists, a request with `url` and no `name` is accepted. The API returns the item immediately with the placeholder name `Loading metadata…`, then starts asynchronous scraping. For wish items with a URL, missing metadata fields are filled from scraped title/description/image/price without overwriting user-entered values. On scrape failure, the item remains so the user's pasted URL is not lost.

Type validation:

- `WISH` items allow shopping fields: `url`, `description`, `imageUrl`, and `price`.
- `CHORE` items reject shopping fields and allow `dueDate` plus `recurrenceRule`.
- `EVENT` items reject shopping fields and `recurrenceRule`, and allow `dueDate`.

### `PUT /api/v1/items/{id}`

Updates an item only when its parent list is owned by the authenticated user. Returns `404` for missing or non-owned items.

### `DELETE /api/v1/items/{id}`

Deletes an item only when its parent list is owned by the authenticated user. Returns `404` for missing or non-owned items.

Item response:

```json
{"id":"uuid","listId":"uuid","name":"Camera strap","description":"Leather strap","url":"https://example.test/strap","imageUrl":null,"price":29.90,"status":"OPEN","dueDate":null,"recurrenceRule":null,"reservedByGuest":null}
```

## Internal sharing

Internal sharing is read-only for MVP. Share management endpoints require the list owner.

### `GET /api/v1/lists/{id}/shares`

Returns users who can read the owned list.

### `POST /api/v1/lists/{id}/shares`

Request:

```json
{"username":"shared-user"}
```

Response: `201 Created` with share.

```json
{"listId":"uuid","userId":"uuid","username":"shared-user","createdAt":"2026-08-31T17:00:00Z"}
```

### `DELETE /api/v1/lists/{id}/shares/{username}`

Revokes read access for the named registered user. Returns `204 No Content`.

## Scraper

### `POST /api/v1/utils/scrape`

Requires authentication. Fetches best-effort metadata for an HTTP(S) URL using Jsoup with browser-like request headers.

Request:

```json
{"url":"https://example.test/product"}
```

Response:

```json
{"title":"Product title","description":"Short description","imageUrl":"https://example.test/image.jpg","price":24.95}
```

Extraction priority:

- Title: OpenGraph, Twitter card, then HTML `<title>`.
- Description: OpenGraph, then standard description meta.
- Image: OpenGraph, then Twitter card, resolved to an absolute URL where possible.
- Price: `product:price:amount`, JSON-LD `offers.price`, then microdata `itemprop=price`.

Non-HTTP(S) URLs return `400 validation_failed`.

## Public access

Public endpoints do not require authentication. Browser links use `/s/{token}` and are served by the SPA; API clients use `/api/v1/share/{token}`.

### `POST /api/v1/lists/{id}/public-share`

Requires ownership. Creates or returns an existing public token.

Response:

```json
{"listId":"uuid","publicList":true,"shareToken":"urlsafetoken","shareUrl":"/s/urlsafetoken"}
```

### `DELETE /api/v1/lists/{id}/public-share`

Requires ownership. Revokes the current public token. Previously issued URLs then return `404`.

### `GET /api/v1/share/{token}`

Returns a safe public representation of a public list and its items. It excludes owner email, owner ID, internal shares, settings, and admin data.

Response:

```json
{"title":"Birthday","description":"Gift ideas","type":"WISH","targetDate":null,"items":[{"id":"uuid","name":"Book","url":"https://example.test/book","imageUrl":null,"price":19.99,"status":"OPEN","dueDate":null,"reservedByGuest":null}]}
```

### `POST /api/v1/share/{token}/items/{itemId}/claim`

Claims an open wishlist item for a guest.

Request:

```json
{"guestName":"Annette"}
```

Responses:

- `200 OK` with updated public item.
- `404 Not Found` when token/item do not match or token was revoked.
- `409 Conflict` with `item_already_claimed` when the item is no longer open.

## Notifications

### `GET /api/v1/notifications`

Requires authentication. Returns unread in-app notifications for the current user, localized by `Accept-Language`.

Response:

```json
[{"id":"uuid","messageKey":"notification.item_due_soon","message":"Water plants is due on 2027-01-01.","readAt":null,"createdAt":"2027-01-01T08:00:00Z"}]
```

### `PUT /api/v1/notifications/{id}/read`

Requires authentication and ownership of the notification. Marks one notification read and returns it. Returns `404` for missing or non-owned notifications.

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

Requires `ADMIN` role. Returns safe user metadata sorted by creation time; password hashes are never serialized.

Response:

```json
[{"id":"uuid","username":"uwe","email":"uwe@example.test","role":"ADMIN","createdAt":"2026-08-31T17:00:00Z"}]
```
