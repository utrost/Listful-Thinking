# Personas and Actors

## Instance Admin

The person who owns and configures the self-hosted instance.

Primary goals:

- Start the app with minimal configuration.
- Create the first account.
- Toggle registration.
- Create, activate, and deactivate users.
- Inspect users, owner metadata, list inventory, and instance settings.
- Keep data local and easy to back up.

Non-goal for MVP:

- Admin list inventory is metadata-focused; admins do not automatically browse every user's private item content.

## Registered User

A local account holder who creates and owns lists.

Primary goals:

- Create wish, to-do, chore, and event lists.
- Add and edit items.
- Share lists with trusted people.
- Use public links when guests should claim wishlist items.
- Receive due/reminder notifications.

## Internal Shared User

A registered user who has been granted access to another user's list.

Primary goals:

- See lists explicitly shared with them.
- Coordinate around shared household/event information.

MVP recommendation:

- Internal shared users are read-only unless a later story explicitly grants collaboration rights.

## Guest

An unauthenticated person using a public share link.

Primary goals:

- Open a share link without creating an account.
- See safe public list information.
- Claim/reserve an open wishlist item by entering a guest name.

Constraints:

- Guests never see owner email, internal user IDs, private shares, or admin data.
- Guests cannot browse other lists.