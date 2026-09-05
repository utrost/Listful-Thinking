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
When registration is disabled after bootstrap, the endpoint returns `403 Forbidden` with `registration_disabled`.

### `GET /api/v1/auth/settings`

Public bootstrap/auth UI settings. This intentionally exposes only whether the self-registration form should be available, not the full admin settings object.

Response:

```json
{"registrationAvailable":false}
```

`registrationAvailable` is `true` when no users exist yet, so the first admin can still bootstrap the instance, or when an admin has enabled registration.

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

### `POST /api/v1/auth/magic-link`

Request:

```json
{"email":"uwe@example.test"}
```

Creates a 30-minute one-time token for the matching email address and sends a magic login link. Always returns `204 No Content` so callers cannot enumerate accounts.

### `POST /api/v1/auth/magic-link/consume`

Request:

```json
{"token":"url-token"}
```

Consumes the one-time token, creates a session, and returns the authenticated user. Invalid, expired, or reused tokens return `401 Unauthorized`.

### `POST /api/v1/auth/password-reset`

Request:

```json
{"email":"uwe@example.test"}
```

Creates a 30-minute one-time token for the matching email address and sends a password reset link. Always returns `204 No Content`.

### `POST /api/v1/auth/password-reset/consume`

Request:

```json
{"token":"url-token","password":"new correct horse battery staple"}
```

Consumes the one-time token and stores a new BCrypt password hash. Invalid, expired, or reused tokens return `401 Unauthorized`.

### `GET /api/v1/auth/me`

Responses:

- `200 OK` with authenticated user.
- `401 Unauthorized` when no session is authenticated.

Authenticated user response:

```json
{"id":"uuid","username":"uwe","email":"uwe@example.test","role":"ADMIN"}
```

## Lists

All list endpoints require an authenticated registered user. `GET /lists/{id}` allows the owner or an internally shared user. Mutating list metadata endpoints remain owner-only.

### `GET /api/v1/lists`

Returns lists owned by the authenticated user plus lists explicitly shared with them.

### `POST /api/v1/lists`

Request:

```json
{"title":"Birthday","description":"Gift ideas","type":"WISH","targetDate":null}
```

Response: `201 Created` with list.

Type validation:

- `EVENT` lists require `targetDate`.
- `WISH`, `TODO`, `GROCERY`, and `CHORE` lists reject `targetDate`; due dates live on supported item types.
- Type validation failures return `400 Bad Request` with `validation_failed`.

### `GET /api/v1/lists/{id}`

Returns owned list, or `404` when the list does not exist or belongs to another user.

### `PUT /api/v1/lists/{id}`

Updates owned list metadata, or returns `404` when not owned.

### `POST /api/v1/lists/{id}/clone`

Creates an owner-only duplicate of a list. The clone copies list metadata and items with fresh list/item IDs, but does not copy public share tokens or internal shares.

Request:

```json
{"title":"Birthday copy"}
```

If `title` is omitted or blank, the clone title defaults to `<source title> copy`. Non-owners receive `404`.

Response: `201 Created` with the cloned list.

### `DELETE /api/v1/lists/{id}`

Deletes owned list, or returns `404` when not owned.

List response:

```json
{"id":"uuid","title":"Birthday","description":"Gift ideas","type":"WISH","publicList":false,"shareToken":null,"publicShareMode":"WISH_CLAIM","targetDate":null,"access":"OWNER","createdAt":"2026-08-31T17:00:00Z"}
```

## Items

All item endpoints require authenticated access to the parent list. Read access allows the owner or an internally shared user. Item create/update allow owners and `CONTRIBUTE` shares. Item delete remains owner-only.

### `GET /api/v1/lists/{id}/items`

Returns items for an owned list, or `404` when the list is not owned.

### `POST /api/v1/lists/{id}/items`

Creates an item on an owned list or a list shared with `CONTRIBUTE` permission.

Request:

```json
{"name":"Camera strap","description":"Leather strap","url":"https://example.test/strap","imageUrl":null,"price":29.90,"status":"OPEN","dueDate":null,"recurrenceRule":null,"quantity":null,"category":null,"ownerLabel":"Responsible role","assistantLabels":"Local assistant"}
```

Response: `201 Created` with item.

URL-only wishlist creation:

```json
{"url":"https://example.test/product"}
```

For `WISH` lists, a request with `url` and no `name` is accepted. The API returns the item immediately with the placeholder name `Loading metadata…`, then starts asynchronous scraping. For wish items with a URL, missing metadata fields are filled from scraped title/description/image/price without overwriting user-entered values. On scrape failure, the item remains so the user's pasted URL is not lost.

Type validation:

- `WISH` items allow shopping fields: `url`, `description`, `imageUrl`, and `price`.
- `TODO` items reject shopping fields and recurrence rules, and allow `dueDate` for reminders.
- `GROCERY` items allow `quantity` and `category`, and reject shopping fields, due dates, and recurrence rules.
- `CHORE` items reject shopping fields and allow `dueDate` plus `recurrenceRule`.
- Supported chore recurrence rules are `FREQ=DAILY`, `FREQ=WEEKLY`, `FREQ=BIWEEKLY`, `FREQ=MONTHLY`, `FREQ=QUARTERLY`, and `FREQ=ANNUALLY`.
- `EVENT` items reject shopping fields and `recurrenceRule`, and allow `dueDate`.
- `ownerLabel` and `assistantLabels` are optional coordination metadata for work-style items. They may name people, roles, bots, or local automations; they do not grant access or notification rights. See [Item Responsibility Metadata](domain/item-responsibility.md) for the full semantics.

### `PUT /api/v1/items/{id}`

Updates an item when its parent list is owned by the authenticated user or shared with `CONTRIBUTE` permission. Returns `404` for missing or inaccessible items.

When a recurring `CHORE` item is updated to `DONE`, the API stores `lastCompletedAt`, advances `dueDate` by the recurrence rule, and returns the item as `OPEN` for the next occurrence.

### `POST /api/v1/items/{id}/skip`

Skips one occurrence of a dated recurring `CHORE` item by advancing its `dueDate` according to its recurrence rule. The item remains `OPEN` and `lastCompletedAt` is unchanged.

### `POST /api/v1/items/{id}/postpone`

Postpones a dated `CHORE` item by a fixed number of days.

Request:

```json
{"days":3}
```

### `DELETE /api/v1/items/{id}`

Deletes an item only when its parent list is owned by the authenticated user. Returns `404` for missing or non-owned items.

### `DELETE /api/v1/lists/{id}/items/completed`

Deletes `DONE` items from an owned `GROCERY` list. This is the shop-mode reset path after a shopping trip. It returns `400 validation_failed` for non-grocery lists and `404` for missing or non-owned lists.

Item response:

```json
{"id":"uuid","listId":"uuid","name":"Camera strap","description":"Leather strap","url":"https://example.test/strap","imageUrl":null,"price":29.90,"status":"OPEN","dueDate":null,"recurrenceRule":null,"quantity":null,"category":null,"reservedByGuest":null,"lastCompletedAt":null,"ownerLabel":"Responsible role","assistantLabels":"Local assistant"}
```

## Internal sharing

Internal sharing supports explicit `READ` and `CONTRIBUTE` permissions. Share management endpoints require the list owner.

### `GET /api/v1/lists/{id}/shares`

Returns users who can access the owned list and their permission.

### `POST /api/v1/lists/{id}/shares`

Request:

```json
{"username":"shared-user","permission":"CONTRIBUTE"}
```

Response: `201 Created` with share.

```json
{"listId":"uuid","userId":"uuid","username":"shared-user","permission":"CONTRIBUTE","createdAt":"2026-08-31T17:00:00Z"}
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

Requires ownership. Creates or updates a public token for the requested mode. If no request body is provided, the default is `WISH_CLAIM` for wish lists and `VIEW` for other list types.

Request:

```json
{"mode":"WISH_CLAIM"}
```

Supported modes:

- `VIEW`: read-only public list view; no guest claiming/signup form.
- `WISH_CLAIM`: wishlist claiming; only valid for `WISH` lists.
- `SIGNUP`: signup sheet for non-wishlist list types; guests reserve open items with their names.

Response:

```json
{"listId":"uuid","publicList":true,"shareToken":"urlsafetoken","shareUrl":"/s/urlsafetoken","mode":"WISH_CLAIM"}
```

### `DELETE /api/v1/lists/{id}/public-share`

Requires ownership. Revokes the current public token. Previously issued URLs then return `404`.

### `GET /api/v1/share/{token}`

Returns a safe public representation of a public list and its items. It excludes owner email, owner ID, internal shares, settings, and admin data.

Response:

```json
{"title":"Birthday","description":"Gift ideas","type":"WISH","targetDate":null,"mode":"WISH_CLAIM","items":[{"id":"uuid","name":"Book","url":"https://example.test/book","imageUrl":null,"price":19.99,"status":"OPEN","dueDate":null,"quantity":null,"category":null,"reservedByGuest":null}]}
```

### `POST /api/v1/share/{token}/items/{itemId}/claim`

Reserves an open public item for a guest. `WISH_CLAIM` mode accepts wishlist items; `SIGNUP` mode accepts non-wishlist items. `VIEW` mode rejects guest claims with `400 validation_failed`.

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
[{"id":"uuid","username":"uwe","email":"uwe@example.test","role":"ADMIN","active":true,"createdAt":"2026-08-31T17:00:00Z"}]
```

### `POST /api/v1/admin/users`

Requires `ADMIN` role. Creates a user directly, independent of public registration settings.

Request:

```json
{"username":"martha","email":"martha@example.test","password":"temporary password","role":"USER"}
```

Response: `201 Created` with safe user metadata. Duplicate usernames return `409 Conflict` with `username_taken`.

### `PATCH /api/v1/admin/users/{id}`

Requires `ADMIN` role. Activates or deactivates a user.

```json
{"active":false}
```

Deactivated users cannot log in or consume magic/password-reset links.

### `GET /api/v1/admin/lists`

Requires `ADMIN` role. Returns all lists with owner metadata for support/oversight.

```json
[{"id":"uuid","title":"Next actions","description":null,"type":"TODO","publicList":false,"ownerId":"uuid","ownerUsername":"uwe","ownerEmail":"uwe@example.test","targetDate":null,"createdAt":"2026-08-31T17:00:00Z"}]
```
