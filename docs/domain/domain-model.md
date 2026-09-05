# Domain Model

This document defines product-domain rules before database and JPA details.

## Users

A user is a local account on one Listful Thinking instance.

Fields:

- `id`
- `username`
- `email`
- `password_hash`
- `role`: `ADMIN` or `USER`
- `active`
- `created_at`

Rules:

- Usernames are unique.
- The first user created on an empty instance becomes `ADMIN`.
- Later public registrations become `USER` when registration is enabled.
- Admin-created users can be created as either `USER` or `ADMIN`.
- Deactivated users cannot log in or use email auth tokens.
- Admin role grants instance management and list metadata inventory, not blanket access to private item content.

## Lists

A list is owned by exactly one registered user.

Fields:

- `id`
- `user_id` owner
- `title`
- `description`
- `type`: `WISH`, `TODO`, `GROCERY`, `CHORE`, or `EVENT`
- `share_token`
- `is_public`
- `public_share_mode`: `VIEW`, `WISH_CLAIM`, or `SIGNUP`
- `target_date`
- `created_at`

Rules:

- Lists are private by default.
- Public sharing is opt-in per list.
- Internal sharing is opt-in per list and per registered user.
- `target_date` is required for `EVENT` lists and rejected for `WISH`, `TODO`, `GROCERY`, and `CHORE` lists.

## Items

An item belongs to exactly one list.

Fields:

- `id`
- `list_id`
- `name`
- `url`
- `image_url`
- `price`
- `status`: `OPEN`, `CLAIMED`, `PURCHASED`, or `DONE` depending on list type
- `due_date`
- `recurrence_rule`
- `quantity`
- `category`
- `reserved_by_guest`
- `owner_label` optional user-visible responsible person/role/agent label
- `assistant_labels` optional user-visible helper/assistant labels

Rules:

- Items inherit access from their parent list.
- Items cannot be accessed independently from authorization context.
- `OPEN` is the default status.
- `CLAIMED` means someone reserved the item, usually through a public wishlist claim or signup link.
- `PURCHASED` is owner-controlled for MVP.
- `DONE` is used by TODO/GROCERY/CHORE/EVENT-style work items.
- Type-specific fields are interpreted by the parent list type.
- `quantity` and `category` are grocery-specific.
- `owner_label` is item-level responsibility metadata, distinct from the registered account that owns the parent list. It may name a household member, roommate, role, or automation/agent label and is intentionally instance-local free text for the MVP.
- `assistant_labels` records optional helpers or assistants that can support or watch the item. Assistants can be people, roles, bots, or local automations; they do not receive permissions by being named here.
- Responsibility labels are display/coordination metadata only. Authorization still follows the parent list and internal/public share rules.

## Settings

Settings are global key/value entries.

Initial keys:

- `registration.enabled`
- future: scheduler time, default language, feature flags

Rules:

- Settings stored in the database override environment defaults.
- Missing settings fall back to environment or app defaults.

## Internal shares

An internal share grants a registered user access to a list they do not own.

Rules:

- The owner can create and revoke shares.
- Existing/missing-permission shares default to read-only `READ` access.
- `CONTRIBUTE` shares allow item contribution but not list/share/public-link management.
- Internal shares never imply admin rights.

## Public shares

A public share is a list-specific token.

Rules:

- Tokens are generated with `SecureRandom` and URL-safe Base64.
- A token grants access only to the safe guest representation of one list.
- `VIEW` public links are read-only.
- `WISH_CLAIM` links allow guest reservations for `WISH` lists only.
- `SIGNUP` links allow guest reservations for non-`WISH` lists only.
- Revocation clears or invalidates the token.
- Re-enabling public sharing should create a new token.

## Notifications

A notification is an in-app message for a user.

Rules:

- Store message key and arguments, not only rendered text.
- Localize at display/email time.
- Notifications belong to one user.
- Reminder-generated notifications should be deduplicated for the same entity/day.