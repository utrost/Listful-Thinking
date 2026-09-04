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

- Create wish, to-do, grocery, chore, and event lists.
- Add and edit items.
- Share lists with trusted people.
- Use public links when guests should view a list, claim wishlist items, or sign up for non-wishlist slots.
- Receive due/reminder notifications.

## Internal Shared User

A registered user who has been granted access to another user's list.

Primary goals:

- See lists explicitly shared with them.
- Coordinate around shared household/event information.

Current behavior:

- Internal shared users may be read-only or contributor-enabled. Contributors can add/edit/complete supported items, but they cannot manage list metadata, shares, public links, or deletion.

## Guest

An unauthenticated person using a public share link.

Primary goals:

- Open a share link without creating an account.
- See safe public list information.
- Depending on the public link mode, read a list, claim/reserve an open wishlist item, or sign up for an open non-wishlist item by entering a guest name.

Constraints:

- Guests never see owner email, internal user IDs, private shares, or admin data.
- Guests cannot browse other lists.