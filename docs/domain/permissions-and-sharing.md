# Permissions and Sharing

Authorization must be centralized in a backend `ListAccessService`. Controllers should not duplicate ownership/share logic.

## Actor permissions matrix

### Owner

Can:

- View owned list and items.
- Create, edit, and delete owned lists.
- Add, edit, and delete items in owned lists.
- Generate and revoke public share tokens.
- Add and revoke internal shares.
- Mark items purchased.

Cannot:

- Access another private list without share/admin-specific future tooling.

### Internal shared user

`READ` shares can:

- View explicitly shared list and items.

`CONTRIBUTE` shares can additionally:

- Add items.
- Edit items.
- Mark supported items done or reopen them.

Shared users cannot:

- Edit list metadata.
- Delete list.
- Delete items.
- Manage shares.
- Generate/revoke public token.

Implemented permission model:

- `list_shares.permission` stores `READ` or `CONTRIBUTE`; missing historical rows default to `READ`.

### Guest via public token

Can:

- View safe public representation of one public list.
- Claim an open item on that list with a guest name.

Cannot:

- Login implicitly.
- Browse all public lists.
- View owner email or internal IDs.
- View internal shares.
- Edit list metadata.
- Add/delete items.
- Mark purchased in MVP.
- Claim items from another list.

### Admin

Can:

- View users.
- Create local users as `USER` or `ADMIN`.
- Activate and deactivate users.
- View all lists as an owner metadata inventory.
- Read/update global settings.
- Enable/disable registration.

MVP cannot by default:

- Browse or mutate private item content owned by other users.

Future extension point:

- If moderation/support access is added, it should be explicit, audited, and documented separately.

## Implemented internal sharing API behavior

- Owners manage internal shares with `GET/POST/DELETE /api/v1/lists/{id}/shares` endpoints.
- Shared users can read the shared list detail and item collection.
- `CONTRIBUTE` shared users can add and edit items. List update/delete, item delete, public-link management, and share management still require ownership.
- Non-owners and non-shared users receive `404` for private resources to avoid leaking existence.

## Implemented public sharing API behavior

- Owners create/revoke public guest links with `POST/DELETE /api/v1/lists/{id}/public-share`.
- Tokens are URL-safe cryptographic random strings and are unique in the local database.
- Public API responses use dedicated DTOs and omit owner email, owner ID, internal shares, settings, admin flags, and password data.
- Guest claiming is limited to WISH lists and only items that belong to the token's list.
- Duplicate claims return `409 item_already_claimed`; revoked or mismatched tokens/items return `404`.

## Required authorization checks

All list and item operations should resolve:

- Current actor.
- Target list.
- Access mode: owner, shared read, public guest, none.
- Requested operation.

Every item operation must validate that the item belongs to the list/token context used for authorization.

## Public DTO rules

Public share responses must exclude:

- Owner email.
- User IDs.
- Internal share records.
- Settings.
- Notification data.
- Admin flags.
- Password hashes.

## Error behavior

- Use 404 for missing resources and private resources that should not be discoverable.
- Use 403 only when the user is authenticated and allowed to know the resource exists.
- Use 409 for duplicate guest claims or invalid status transitions.
- Localize error messages through backend `MessageSource`.