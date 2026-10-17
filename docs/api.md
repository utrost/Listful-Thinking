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

Public bootstrap/auth UI settings. This exposes registration availability and whether an email recovery host is configured, without returning the full admin settings object.

Response:

```json
{"registrationAvailable":false,"emailRecoveryAvailable":true}
```

`registrationAvailable` is `true` when no users exist yet, so the first admin can still bootstrap the instance, or when an admin has enabled registration.
`emailRecoveryAvailable` reports whether a mail host is configured; it does not prove delivery or expose mail settings.

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
{"email":"uwe@example.test","username":"uwe"}
```

Creates a 30-minute one-time token for one matching active account and sends a magic login link. `username` is optional but disambiguates a shared email address. Returns a neutral `204 No Content` for unknown or ambiguous accounts so callers cannot enumerate them. Unavailable or failed mail delivery returns `503`.

### `POST /api/v1/auth/magic-link/consume`

Request:

```json
{"token":"url-token"}
```

Consumes the one-time token, creates a session, and returns the authenticated user. Invalid, expired, or reused tokens return `401 Unauthorized`.

### `POST /api/v1/auth/password-reset`

Request:

```json
{"email":"uwe@example.test","username":"uwe"}
```

Creates a 30-minute one-time token for one matching active account and sends a password reset link. `username` is optional for shared email addresses. Unknown or ambiguous accounts receive a neutral `204 No Content`; unavailable or failed mail delivery returns `503`.

### `POST /api/v1/auth/password-reset/consume`

Request:

```json
{"token":"url-token","password":"new correct horse battery staple"}
```

Consumes the one-time token and stores a new BCrypt password hash. Invalid, expired, or reused tokens return `401 Unauthorized`. An admin-issued account setup/recovery link uses this same consume endpoint, but remains valid for 24 hours instead of 30 minutes. Consuming either kind of reset token invalidates the account's previous sessions and outstanding account tokens.

### `POST /api/v1/auth/change-password`

Requires an authenticated session and the current password.

```json
{"currentPassword":"current secret","newPassword":"new correct horse battery staple"}
```

Returns `204 No Content` on success. Existing sessions and outstanding account links are invalidated; the user must sign in again. A wrong current password returns `401 bad_credentials`. New passwords must be 8–72 characters and at most 72 UTF-8 bytes.

### `GET /api/v1/auth/me`

Responses:

- `200 OK` with authenticated user.
- `401 Unauthorized` when no session is authenticated.

Authenticated user response:

```json
{"id":"uuid","username":"uwe","email":"uwe@example.test","role":"ADMIN"}
```

## Lists

All list endpoints require an authenticated registered user. Active list reads allow the owner or an internally shared user. Mutating list metadata endpoints remain owner-only.

### `GET /api/v1/lists`

Returns active lists owned by the authenticated user plus active lists explicitly shared with them.

### `POST /api/v1/lists`

Request:

```json
{"title":"Birthday","description":"Gift ideas","type":"WISH","targetDate":null,"sourceUrl":null,"eventLocation":null}
```

Response: `201 Created` with list.

Type validation:

- `EVENT` lists require `targetDate`.
- `eventLocation` is accepted only for `EVENT` lists. `sourceUrl`, when set, must be HTTP(S).
- `WISH`, `TODO`, `GROCERY`, and `CHORE` lists reject `targetDate`; due dates live on supported item types.
- Type validation failures return `400 Bad Request` with `validation_failed`.

### `GET /api/v1/lists/{id}`

Returns a non-deleted list to its owner; an internally shared user can read it only while it is active. Returns `404` when inaccessible. Owner-only library endpoints enumerate archived, trashed, and template lists; ordinary reads of trashed lists return `404`.

### `PUT /api/v1/lists/{id}`

Updates owned list metadata, or returns `404` when not owned.

This is a full metadata update: send existing `sourceUrl` and `eventLocation` values when they should be preserved. An omitted nullable field clears that field.

### `POST /api/v1/lists/{id}/clone`

Creates an owner-only duplicate of a list. The clone copies list metadata and items with fresh list/item IDs, but does not copy public share tokens or internal shares.

Request:

```json
{"title":"Birthday copy"}
```

If `title` is omitted or blank, the clone title defaults to `<source title> copy`. Non-owners receive `404`.

Response: `201 Created` with the cloned list.

### `DELETE /api/v1/lists/{id}`

Moves an owned list to Trash, or returns `404` when not owned. The daily retention job permanently purges trashed records after 14 days.

### `POST /api/v1/lists/{id}/transfer`

Requires ownership. Body: `{"username":"martha"}`. The target must be a different active account. Returns `204 No Content`; the old owner loses access unless the new owner shares the list back. Existing internal shares remain, any share for the new owner is removed as redundant, the public link is revoked, both users receive in-app notifications, and the handoff is audited.

List response:

```json
{"id":"uuid","title":"Birthday","description":"Gift ideas","type":"WISH","publicList":false,"shareToken":null,"publicShareMode":"WISH_CLAIM","publicShareExpiresAt":null,"targetDate":null,"sourceUrl":null,"eventLocation":null,"access":"OWNER","createdAt":"2026-08-31T17:00:00Z"}
```

## Items

All item endpoints require authenticated access to the parent list. Read access allows the owner or an internally shared user. Item create/update allow owners and `CONTRIBUTE` shares. Item delete remains owner-only.

### `GET /api/v1/lists/{id}/items`

Returns items from a non-deleted list to its owner; an internally shared user can read items only while the list is active. Returns `404` when inaccessible.

### `POST /api/v1/lists/{id}/items`

Creates an item on an owned list or a list shared with `CONTRIBUTE` permission.

Request:

```json
{"name":"Camera strap","description":"Leather strap","url":"https://example.test/strap","sourceUrl":"https://example.test/strap","imageUrl":null,"price":29.90,"priceCurrency":"EUR","variantNote":"Black, 35 mm","seller":"Example Shop","availability":"InStock","status":"OPEN"}
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
- `ownerLabel` and `assistantLabels` are optional coordination metadata for work-style `TODO`, `CHORE`, and `EVENT` items. They may name people, roles, bots, or local automations; they do not grant access or notification rights. `WISH` and `GROCERY` items reject non-blank responsibility labels. See [Item Responsibility Metadata](domain/item-responsibility.md) for the full semantics.
- `sourceUrl` can preserve an HTTP(S) page that inspired any item. Wishlist items can additionally record `variantNote`, `seller`, and `availability`. `priceCheckedAt` is server-maintained when a price is set or refreshed.

### `PUT /api/v1/items/{id}`

Updates an item when its parent list is owned by the authenticated user or shared with `CONTRIBUTE` permission. Send the current item version as `If-Match: "<version>"`; missing or stale versions return `409 stale_item`. Returns `404` for missing or inaccessible items.

This is a full item update. Include existing capture fields (`sourceUrl`, `variantNote`, `seller`, `availability`, and price/currency) when they should be retained; omitted nullable fields are cleared. The frontend sends the current values during edits and status changes.

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

Moves an item to Trash only when its parent list is owned by the authenticated user. Returns `404` for missing or non-owned items.

### `DELETE /api/v1/lists/{id}/items/completed`

Moves `DONE` items from an owned `GROCERY` list to Trash. This is the shop-mode reset path after a shopping trip. It returns `400 validation_failed` for non-grocery lists and `404` for missing or non-owned lists.

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

Response: `201 Created` with share. An active recipient receives an in-app notification when access is first granted or updated.

```json
{"listId":"uuid","userId":"uuid","username":"shared-user","permission":"CONTRIBUTE","createdAt":"2026-08-31T17:00:00Z"}
```

### `DELETE /api/v1/lists/{id}/shares/{username}`

Revokes access for the named registered user. Returns `204 No Content`. An active recipient receives an in-app notification.

## Scraper

### `POST /api/v1/utils/scrape`

Requires authentication. Fetches best-effort metadata for an HTTP(S) URL using a validated-address fetch path and HtmlUnit parsing with browser-like request headers.

Request:

```json
{"url":"https://example.test/product"}
```

Response:

```json
{"title":"Product title","description":"Short description","imageUrl":"https://example.test/image.jpg","price":24.95,"priceCurrency":"EUR"}
```

Extraction priority:

- Title and description: schema.org `Product` JSON-LD, then OpenGraph/Twitter and HTML fallbacks.
- Image: schema.org `Product` JSON-LD, then OpenGraph/Twitter and common product-image selectors, resolved to an absolute URL where possible.
- Price and currency: the first useful JSON-LD offer, then social metadata and microdata price fallbacks.

The wishlist UI applies this response directly to the existing editable item form. A scrape failure is shown to the user and does not prevent manual item creation.

### `POST /api/v1/utils/extract`

Requires authentication and uses the same guarded HTTP(S) fetch path as `/utils/scrape`. Request: `{"url":"https://example.test/page"}`. Response is a draft for user review:

```json
{"kind":"RECIPE","sourceUrl":"https://example.test/recipe","extractedAt":"2026-10-17T08:00:00Z","title":"Pancakes","description":null,"imageUrl":null,"price":null,"priceCurrency":null,"seller":null,"availability":null,"ingredients":["2 eggs","200 g flour"],"steps":["Mix ingredients"],"eventStart":null,"eventLocation":null}
```

`kind` is `PRODUCT`, `RECIPE`, `EVENT`, `HOWTO`, or `PAGE`. Clients should allow correction before persisting. A failed read need not prevent manual entry with the original source URL.

`EVENT` includes recognized Schema.org event subtypes such as `MusicEvent`. `sourceUrl` is the final URL after validated redirects; it may differ from the submitted URL or point to a localized page. The draft is transient until the user saves it.

Non-HTTP(S) URLs return `400 validation_failed`.

## Public access

Public endpoints do not require authentication. Browser links use `/s/{token}` and are served by the SPA; API clients use `/api/v1/share/{token}`.

### `POST /api/v1/lists/{id}/public-share`

Requires ownership. Creates or updates a public token for the requested mode. If no request body is provided, the default is `WISH_CLAIM` for wish lists and `VIEW` for other list types.
The token expires 56 days after creation. Existing links at the V17 upgrade receive 56 days from upgrade. Expired public reads and claims return `404`, and the owner can create a fresh link.

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
{"listId":"uuid","publicList":true,"shareToken":"urlsafetoken","shareUrl":"/s/urlsafetoken","mode":"WISH_CLAIM","expiresAt":"2026-12-11T17:00:00Z"}
```

### `DELETE /api/v1/lists/{id}/public-share`

Requires ownership. Revokes the current public token. Previously issued URLs then return `404`.

### `GET /api/v1/share/{token}`

Returns a safe public representation of a public list and its items. It excludes owner email, owner ID, internal shares, settings, and admin data.

Response:

```json
{"title":"Birthday","description":"Gift ideas","type":"WISH","targetDate":null,"sourceUrl":null,"eventLocation":null,"mode":"WISH_CLAIM","items":[{"id":"uuid","name":"Book","url":"https://example.test/book","imageUrl":null,"price":19.99,"status":"OPEN","dueDate":null,"quantity":null,"category":null,"reservedByGuest":null}]}
```

### `POST /api/v1/share/{token}/items/{itemId}/claim`

Reserves an open public item for a guest. `WISH_CLAIM` mode accepts wishlist items; `SIGNUP` mode accepts non-wishlist items. `VIEW` mode rejects guest claims with `400 validation_failed`.

Request:

```json
{"guestName":"Annette"}
```

Responses:

- `200 OK` with updated public item and a `claimToken` revealed only in the claim response. Store it privately for later correction or release; the token can be reused for those controls while the claim and public link remain active. Subsequent public wishlist reads hide guest names from the owner and other guests.
- `404 Not Found` when token/item do not match or token was revoked.
- `409 Conflict` with `item_already_claimed` when the item is no longer open.

### Guest claim controls

- `POST /api/v1/share/{token}/items/{itemId}/rename` with `{"claimToken":"...","guestName":"Corrected name"}` changes the claimant name.
- `POST /api/v1/share/{token}/items/{itemId}/release` with `{"claimToken":"..."}` releases the guest reservation.
- `POST /api/v1/items/{itemId}/release-claim` lets the list owner release a mistaken reservation while authenticated.

Guest control tokens are random, stored only as hashes, and remain valid only for the matching active public share and claimed item. A released item can be claimed again.

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

### `POST /api/v1/admin/users/with-setup`

Requires `ADMIN` role. Creates a `USER` or `ADMIN` without an admin-chosen password and issues a one-time setup link valid for 24 hours, independent of public registration settings.

```json
{"username":"martha","email":"martha@example.test","role":"USER"}
```

Returns `201 Created` with safe user metadata and setup-link details:

```json
{"user":{"id":"uuid","username":"martha","email":"martha@example.test","role":"USER","active":true,"createdAt":"2026-10-16T12:00:00Z"},"setupUrl":"https://lists.example.org/reset-password?token=example","expiresAt":"2026-10-17T12:00:00Z","emailed":false}
```

If SMTP sends the link to the account email, `emailed` is `true` and `setupUrl` is `null`. A returned URL is shown once; pass it privately to the user. Duplicate usernames return `409 username_taken`.

### `POST /api/v1/admin/users/{id}/setup-link`

Requires `ADMIN` role and an active target account. Issues a fresh 24-hour one-time setup/recovery link with the same response shape as above. Earlier login, setup, and reset tokens for that account are invalidated. Returns `409 inactive_user` for an inactive target. Raw links are excluded from audit details.

### `PATCH /api/v1/admin/users/{id}`

Requires `ADMIN` role. Activates or deactivates a user.

```json
{"active":false}
```

Deactivated users cannot log in or consume magic/password-reset links.

### `POST /api/v1/admin/users/{id}/transfer-lists`

Requires `ADMIN` role. Body: `{"username":"martha"}`. The source account must be inactive; the target must be active, different from the source, and different from the acting admin. Transfers every owned list, including archived lists, templates, and Trash, while revoking public links and keeping unrelated internal shares. Returns `200 OK` with `{"transferred":3}`. Errors include `409 owner_active`, `inactive_user`, `same_owner`, `self_transfer`, or `no_lists_to_transfer`. The transfer is audited without exposing list titles to the admin.

### `GET /api/v1/admin/lists`

Requires `ADMIN` role. Returns per-user list counts only; administration does not grant access to private list content.

```json
[{"ownerId":"uuid","ownerUsername":"uwe","listCount":3}]
```

## September 2026 reliability changes

- Item responses include `version`, `priceCurrency`, and `importStatus` (`NONE`, `PENDING`, `READY`, `FAILED`).
- `GET /api/v1/items/{id}` returns a readable item.
- `PUT /api/v1/items/{id}` requires `If-Match: "<version>"`. Read the version first; missing/stale values return `409 stale_item`. Never automatically overwrite a stale edit.
- `POST /api/v1/items/{id}/import` retries metadata import for a contributable wishlist item with a URL. It returns the item with pending state and is rate-limited.
- `priceCurrency` is an optional three-uppercase-letter code. Public item responses also contain it.
- Email-link/reset request bodies accept optional `username` alongside `email`; this is required to disambiguate shared family addresses. Ambiguous requests still return 204.
- Changing the type of a populated list returns `409 list_type_in_use`.
- `GET /api/v1/health` is liveness; `GET /api/v1/health/ready` checks database access and returns 503 when unavailable.

## Planning, archive, recovery and templates (0.2.0-rc.1)

All endpoints below require a session. Library and restore endpoints are owner-only; overview entries include only active owned/shared lists.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| GET | `/api/v1/overview?zone=Europe/Berlin` | Returns `date`, `zone`, `overdue`, `today`, `upcoming`. Zone defaults to UTC; invalid zones return 400. Entries have kind ITEM/EVENT, ID/list metadata, dueDate, access, version, recurrence and responsibility label. Images/private account data are omitted. |
| GET | `/api/v1/lists/library?state=archive` | Owner's archive; states `archive`, `trash`, `templates` accepted. Invalid states return 400. |
| GET | `/api/v1/trash/items` | Owner's deleted items whose parent is not deleted, with parent title/archive state. |
| POST | `/api/v1/lists/{id}/archive` | Body `{"archived":true}` pauses a list; false resumes it. Templates cannot be archived. |
| POST | `/api/v1/lists/{id}/restore` | Restore owner's trashed list, clear archive flag, retain internal shares. Public links stay revoked. Idempotent. |
| POST | `/api/v1/items/{id}/restore` | Restore owner's deleted item; parent must exist and not be archived. Revisions advance. Idempotent. |
| POST | `/api/v1/lists/{id}/template` | Body `{"title":"Packing blueprint"}` saves a private fresh template copy. |
| POST | `/api/v1/lists/{id}/instantiate` | Body `{"title":"Summer trip"}` creates a fresh private copy of an owned template; EVENT requires `targetDate`. |

List responses additionally expose `archived`, `template`, and nullable `deletedAt`. Ordinary list collections contain only active lists. DELETE list/item and clear-completed now soft-delete into Trash; direct ordinary reads of deleted records return 404. Archive/Trash/templates are excluded from future reminder scans and public views. Only owners can inspect non-active lists. Archived item mutation is prohibited, and archived/template sharing returns `409 list_inactive`. Template instances reset status, reservation/history, item dates, and shares; content and recurrence definitions are copied. Item PUT still requires If-Match; stale revisions return 409.
List responses also expose nullable `publicShareExpiresAt`; `publicList` becomes false once the link expires. Trash records are permanently removed by a daily job after 14 days. Purging a trashed list also removes its items and internal shares.
