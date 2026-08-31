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
- `created_at`

Rules:

- Usernames are unique.
- The first user created on an empty instance becomes `ADMIN`.
- Later users become `USER` unless explicitly changed by admin functionality in a future story.
- Admin role grants instance management, not blanket access to private list content.

## Lists

A list is owned by exactly one registered user.

Fields:

- `id`
- `user_id` owner
- `title`
- `description`
- `type`: `WISH`, `CHORE`, or `EVENT`
- `share_token`
- `is_public`
- `target_date`
- `created_at`

Rules:

- Lists are private by default.
- Public sharing is opt-in per list.
- Internal sharing is opt-in per list and per registered user.
- `target_date` is meaningful for `EVENT` lists and optional/ignored for other types unless future stories expand it.

## Items

An item belongs to exactly one list.

Fields:

- `id`
- `list_id`
- `name`
- `url`
- `image_url`
- `price`
- `status`: `OPEN`, `CLAIMED`, `PURCHASED`
- `due_date`
- `recurrence_rule`
- `reserved_by_guest`

Rules:

- Items inherit access from their parent list.
- Items cannot be accessed independently from authorization context.
- `OPEN` is the default status.
- `CLAIMED` means someone reserved the item, usually through a public wishlist link.
- `PURCHASED` is owner-controlled for MVP.
- Type-specific fields are interpreted by the parent list type.

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
- MVP default: shared access is read-only.
- Internal shares never imply admin rights.

## Public shares

A public share is a list-specific token.

Rules:

- Tokens are generated with `SecureRandom` and URL-safe Base64.
- A token grants access only to the safe guest representation of one list.
- Revocation clears or invalidates the token.
- Re-enabling public sharing should create a new token.

## Notifications

A notification is an in-app message for a user.

Rules:

- Store message key and arguments, not only rendered text.
- Localize at display/email time.
- Notifications belong to one user.
- Reminder-generated notifications should be deduplicated for the same entity/day.