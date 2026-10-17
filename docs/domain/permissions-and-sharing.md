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
- Release a mistaken guest reservation on an owned item.
- Transfer an owned list to another active account.

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
- In `VIEW` mode: read only, with no guest mutation form.
- In `WISH_CLAIM` mode: claim an open item on a `WISH` list with a guest name.
- In `SIGNUP` mode: reserve/sign up for an open item on a non-`WISH` list with a guest name.
- After claiming, rename or release their own reservation when they present the claim-control token returned to that claimant.

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
- View per-user list counts; private titles, descriptions and dates are not an administrative inventory.
- Read/update global settings.
- Enable/disable registration.
- Issue one-time account setup/recovery links.
- Transfer every list from an inactive owner to a different active account, including archived lists, templates, and Trash, without seeing private list titles or contents.

MVP cannot by default:

- Browse or mutate private item content owned by other users.

Future extension point:

- If moderation/support access is added, it should be explicit, audited, and documented separately.

## Implemented internal sharing API behavior

- Owners manage internal shares with `GET/POST/DELETE /api/v1/lists/{id}/shares` endpoints.
- Shared users can read the shared list detail and item collection.
- `CONTRIBUTE` shared users can add and edit items. List update/delete, item delete, public-link management, and share management still require ownership.
- Active recipients receive in-app notifications when a share is created, updated, or revoked.
- Non-owners and non-shared users receive `404` for private resources to avoid leaking existence.

## Ownership handoffs

- An owner calls `POST /api/v1/lists/{id}/transfer` with the target username. The target must be a different active account. The previous owner loses access unless the new owner shares the list back.
- An admin calls `POST /api/v1/admin/users/{id}/transfer-lists` only after deactivating the source account. The target cannot be the acting admin. The response returns a transferred count, not list content.
- Both paths revoke active public links, retain unrelated internal shares, remove a redundant share for the new owner, notify the old and new owners, and create a required security audit row. Admin handoff is transactional across all owned lists.

## Implemented public sharing API behavior

- Owners create/revoke public guest links with `POST/DELETE /api/v1/lists/{id}/public-share`.
- Tokens are URL-safe cryptographic random strings and are unique in the local database.
- Public API responses use dedicated DTOs and omit owner email, owner ID, internal shares, settings, admin flags, and password data.
- Public links have explicit modes: `VIEW`, `WISH_CLAIM`, and `SIGNUP`.
- `WISH_CLAIM` is valid only for `WISH` lists; `SIGNUP` is valid only for non-`WISH` lists; `VIEW` is read-only for all list types.
- Guest claiming/signup is allowed only in the mode intended for the list type and only for items that belong to the token's list.
- Claim-control tokens are random and stored only as hashes. Rename/release requires the matching active public link, item, and claim token; a released item can be claimed again. The owner can release a mistaken claim through the authenticated item API.
- Wishlist guest names are hidden from the list owner's item response and from later public reads. The claimant sees the name on the immediate claim/rename response. Non-wishlist `SIGNUP` labels remain visible for coordination.
- Duplicate claims/signups return `409 item_already_claimed`; revoked or mismatched tokens/items return `404`.
- New public links expire after 56 days; existing links were given 56 days at the V17 upgrade. Expired reads and claims return `404`.

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
