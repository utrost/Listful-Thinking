# User and Admin Guide

This guide describes what a regular user and an admin can do in the current Listful Thinking app. It is written for people using the app, not for developers reading the API.

## Roles at a glance

### Regular user

A regular user is a local account holder on one Listful Thinking instance.

A user can:

- Log in and log out.
- Request a magic login link when their account has an email address and mail is configured.
- Request a password reset link when their account has an email address and mail is configured.
- Create and manage their own lists.
- Add, edit, and delete items in their own lists.
- Mark to-do, grocery, chore, and event items done and reopen them if needed.
- See lists that other users explicitly shared with them.
- Create public guest links for their own lists.
- Revoke public guest links for their own lists.
- Share their own lists read-only or contributor-enabled with another registered user.
- Revoke internal shares they created.
- Receive in-app notifications for reminders.
- Receive email reminders when SMTP is configured.

A user cannot:

- See another user's private lists unless the owner shared the list with them.
- Edit a list that was only shared with them read-only.
- Manage other users.
- Enable or disable registration.
- Access admin settings or admin inventory pages.

### Admin

An admin is a local account holder with instance-management rights.

An admin can do everything a regular user can do for their own lists, plus:

- Use the Admin panel.
- Enable or disable public registration.
- Create new local users while registration is disabled.
- Choose whether a newly created user is `USER` or `ADMIN`.
- See all users with username, email, role, active state, and creation time.
- Activate or deactivate user accounts.
- See an instance-wide list inventory with owner metadata.

An admin cannot, by default:

- Browse or edit the private item contents of another user's list from the admin inventory.
- Silently become another user.
- Use admin status as a public guest identity.

Admin list visibility is metadata-focused: it is meant for operating a small private instance, finding abandoned lists, and understanding ownership without turning admin into a hidden content-browsing role.

## Account access

### Username and password

Users can log in with their username and password. Successful login creates a browser session. Logging out ends that session.

### Magic links

If a user has an email address and the instance has SMTP configured, they can request a magic login link. The app sends a one-time link that signs them in.

Important behavior:

- The token is one-time use.
- The token expires after 30 minutes.
- The request does not reveal whether an email address exists.
- Deactivated users cannot use magic-link tokens.

### Password reset

If a user has an email address and SMTP is configured, they can request a password reset link.

Important behavior:

- The token is one-time use.
- The token expires after 30 minutes.
- The request does not reveal whether an email address exists.
- Deactivated users cannot use password-reset tokens.

### When email is not configured

Listful Thinking remains usable without email. Users can still log in with username/password, and reminders fall back to in-app notifications. Email-based login, password reset, and email reminders only send mail when SMTP settings are complete.

## Lists users can create

Users can create typed lists. The list type controls which fields are shown and which fields the backend accepts.

### WISH

Use for gift ideas and public gift claiming.

Typical user actions:

- Add a gift idea manually.
- Paste a product URL.
- Preview URL metadata before saving.
- Create a URL-only item and let the app enrich title, description, image, and price when possible.
- Generate a public guest link.
- Let guests claim open wishlist items.
- Mark items as purchased.

Fields:

- name
- description
- URL
- image URL
- price
- status

Status meaning:

- `OPEN`: still available as a gift idea.
- `CLAIMED`: a guest reserved the item from a public link.
- `PURCHASED`: the owner marked it bought/handled.

### TODO

Use for one-off personal tasks and follow-ups.

Typical user actions:

- Add a task.
- Add an optional due date/time.
- Receive reminders for due items.
- Mark task status as it changes.

Fields:

- name
- due date/time
- status

TODO lists do not use shopping fields or recurrence rules.

Status meaning:

- `OPEN`: still to do.
- `DONE`: completed; due-date reminders stop for this item.

### GROCERY

Use for weekly shopping or errands.

Typical user actions:

- Add a grocery item.
- Add quantity such as `2 cartons`, `500g`, or `1 pack`.
- Add category such as `Vegetables`, `Dairy alternatives`, or `Cleaning`.
- Use item status to track what is still open or already handled.

Fields:

- name
- quantity
- category
- status

Grocery lists do not use product URLs, image URLs, prices, due dates, or recurrence rules in the current slice.

Status meaning:

- `OPEN`: still to buy/handle.
- `DONE`: bought/handled and ready to reopen if needed.

Shop mode:

- Grocery items are grouped by category in the list detail view.
- Items without a category appear under **Uncategorized**.
- Use **Done** as the one-tap check-off while shopping.
- Use **Hide completed** to keep only remaining open items visible on a phone.
- List owners can use **Clear completed** after the trip. This deletes only `DONE` grocery items from that grocery list. It does not clear completed TODO, CHORE, EVENT, or WISH items.

### CHORE

Use for household work and recurring duties.

Typical user actions:

- Add a chore.
- Add a due date/time.
- Add a recurrence rule such as `FREQ=WEEKLY`.
- Receive reminders for due chores.

Fields:

- name
- due date/time
- recurrence rule
- status

CHORE lists do not use shopping fields.

Status meaning:

- `OPEN`: still due.
- `DONE`: completed for one-off chores; for recurring chores the app immediately advances the due date and keeps the item open for the next occurrence.

Recurring chores:

- Choose daily, weekly, every-two-weeks, monthly, quarterly, or annual recurrence instead of typing a raw rule.
- Completing a recurring chore records the last completion time and advances the due date to the next occurrence.
- Use **Skip** to move a recurring chore to its next occurrence without marking it completed.
- Use **Postpone 1 day** for a small delay without changing the recurrence rule.
- Unsupported recurrence rules are rejected so reminders do not silently drift.

### EVENT

Use for planning around a target date, such as a birthday, trip, party, or school event.

Typical user actions:

- Create a list with a required event target date.
- Add preparation items.
- Add optional item-level due dates.
- Receive reminders for upcoming event deadlines or due items.

Fields:

- list target date
- item name
- item due date/time
- status

EVENT items do not use shopping fields or recurrence rules in the MVP.

Status meaning:

- `OPEN`: still part of the event preparation.
- `DONE`: completed; item-level reminders stop for this item.

## Working with items

List owners can edit their own items after creation. The edit form shows the same type-specific fields as the create form, so grocery items expose quantity/category, wish items expose URL/image/price, and dated items expose due-date controls.

Use the item review controls on a selected list to keep longer lists navigable:

- Search matches item name, notes/description, category, and URL.
- Filters show all, open, completed, claimed, purchased, overdue, or upcoming items.
- Sorting can keep the current created order or order by due date, category, or status.

Review controls are client-side in the current slice: they work on the items already loaded for the selected list and do not expose other users' private lists.

For `TODO`, `GROCERY`, `CHORE`, and `EVENT` lists, use **Done** to complete an item and **Reopen** to move one-off completed items back to `OPEN`. Done items do not create due-date reminders. Recurring chores are special: **Done** records a completion and immediately advances the due date, so the item stays `OPEN` for its next occurrence.

For `WISH` lists, the lifecycle stays gift-specific: guests can claim open items from a public link, and owners can use `PURCHASED` for bought items.

## Working with list metadata

List owners can correct list metadata from the selected list detail panel.

Editable list fields:

- title
- description
- type
- event target date, for `EVENT` lists only

Changing a list type changes which item fields are shown and accepted for future item edits. Use this mainly to correct a wrongly chosen type early; if a list already contains many type-specific items, create a fresh list instead of using type changes as a conversion tool.

Deleting a list is destructive. The UI now asks for a second confirmation step before calling delete. Deleting a list removes its items, internal shares, and public link state with the list.

## Sharing

### Internal sharing with registered users

A list owner can share a list with another local user by username.

The shared user can always:

- See the shared list.
- Read the list's items.

If the owner grants `CONTRIBUTE` permission, the shared user can also:

- Add items.
- Edit items.
- Mark supported items done or reopen them.

The shared user cannot, in the current MVP:

- Edit the list.
- Delete the list.
- Delete items.
- Manage shares.
- Generate or revoke public guest links.

The list owner can revoke the internal share at any time.

### Public guest links

A list owner can create a public link for one list. The link is intended for guests who should not need an account.

Guests can:

- Open the public link without logging in.
- See a safe public version of the list.
- In `VIEW` mode: read the public list only.
- In `WISH_CLAIM` mode: claim an open wishlist item by entering their name.
- In `SIGNUP` mode: sign up for / reserve an open non-wishlist item by entering their name.

Guests cannot:

- Browse other lists.
- See owner email addresses or internal user IDs.
- See internal sharing records.
- See admin data.
- Edit list metadata.
- Add or delete items.
- Claim items from a different list.

Public guest mutation is mode-specific: `WISH_CLAIM` is only for `WISH` lists, `SIGNUP` is only for non-`WISH` lists, and `VIEW` is read-only. A list owner can revoke a public link so the old URL stops working.

## Notifications and reminders

The app can create reminders for dated work:

- TODO item due dates
- CHORE item due dates
- EVENT target dates
- EVENT item due dates

Delivery behavior:

- If SMTP is configured, the app can send email reminders.
- If SMTP is missing or incomplete, the app creates in-app notifications instead.
- Duplicate notifications for the same entity/day are avoided.

GROCERY and WISH lists do not currently trigger due-date reminders.

## Admin workflows

### First admin bootstrap

On an empty instance, the first registered account becomes `ADMIN`. This allows a self-hoster to start the app without a separate setup script.

After bootstrap, public registration is disabled by default unless an admin enables it.

### Registration toggle

An admin can enable or disable registration in the Admin panel.

- Enabled: new people can register themselves as regular `USER` accounts.
- Disabled: public self-registration is closed, but admins can still create users manually.

### Creating users manually

Admins can create accounts from the Admin panel.

Required:

- username
- password
- role: `USER` or `ADMIN`

Optional:

- email address

Use this when the instance is private and the admin wants to add family members or collaborators without opening public registration.

### Deactivating users

Admins can deactivate users instead of deleting them.

A deactivated user:

- cannot log in with username/password.
- cannot use magic-link login.
- cannot use password-reset links.

Existing data is preserved. Admins can reactivate the account later.

### List inventory

Admins can see a list inventory across the instance.

The inventory shows list metadata such as:

- list title
- list type
- public/private state
- owner username
- owner email, when present
- creation time

The inventory is not a private item-content browser.

## Practical examples

### Private family wishlist

1. User creates a `WISH` list called `Birthday`.
2. User pastes product URLs or adds gift ideas manually.
3. User creates a public link.
4. Guests open the link and claim items with their names.
5. User revokes the link after the event.

### Weekly grocery list

1. User creates a `GROCERY` list called `Groceries`.
2. User adds `Oat milk` with quantity `2 cartons` and category `Dairy alternatives`.
3. User adds other items with categories matching the shop layout.
4. User updates item status while shopping.

### Personal reminder list

1. User creates a `TODO` list called `Next actions`.
2. User adds `Call optician` with a due date/time.
3. App creates an in-app notification or email reminder when the item is due soon.

### Private household chores

1. User creates a `CHORE` list called `Household`.
2. User adds `Water plants` with a due date and recurrence rule.
3. The app reminds the user when the chore is due.
4. The owner can share the list read-only with another local account.

## Which list type should I choose?

- Use **WISH** when other people may reserve gift ideas from a public link.
- Use **TODO** for one-off personal tasks or follow-ups with optional due dates.
- Use **GROCERY** for shopping and errands where quantity, category grouping, fast Done/Reopen, Hide completed, and Clear completed matter.
- Use **CHORE** for household work that can recur daily, weekly, or monthly, or that needs a last-completed record.
- Use **EVENT** for preparation around a target date, such as a trip, birthday, party, or school deadline. `SIGNUP` public links turn event items into a simple guest signup sheet.

If you picked the wrong type early, edit the list type from the list detail panel. If the list already has many type-specific items, it is safer to create a new list or duplicate a close existing list and clean it up.

## What happens when I share?

### Private by default

New lists are private to the owner. Other local accounts cannot see them unless the owner creates an internal share or public link.

### Internal registered-user sharing

Internal sharing is for trusted users on the same instance:

- **Read-only** recipients can view the shared list and items but cannot change them.
- **Contributor** recipients can add, edit, complete, and reopen items where the list type permits it.
- Only the owner can edit list metadata, delete the list, delete items, manage internal shares, or manage public links.
- Revoking an internal share removes the recipient's access from their account.

### Public guest links

Public links are for people without accounts. The owner chooses a mode when creating the link:

- `VIEW`: read-only public list view; no guest form.
- `WISH_CLAIM`: wishlist reservation flow for `WISH` lists.
- `SIGNUP`: signup-sheet flow for non-wishlist lists, useful for event jobs, chores, or bring-along lists.

Public links expose one safe list view, not the owner's whole account. Guests do not see owner email addresses, internal user IDs, internal share records, admin data, notifications, or private lists. Revoking the public link makes the old URL stop working.

### Admin visibility

Admins can see user and list metadata needed to operate the instance. The current admin inventory is not a hidden private item-content browser. A future break-glass support view, if added, should be explicit and audited.

## Troubleshooting

### Magic-link or password-reset email does not arrive

- Check whether the user account has an email address.
- Check that SMTP is fully configured: host, port, user, and password must all be present.
- Try username/password login; the app remains usable without email.
- Ask an admin to verify that the account is active. Deactivated users cannot use magic-login or password-reset tokens.
- Token links expire after 30 minutes and are one-time use; request a new link if the old one was opened already.

### URL metadata scraping fails or creates a placeholder item

- The item is intentionally kept so the pasted URL is not lost.
- Some shops block server-side requests or hide metadata from simple HTTP fetches.
- Edit the item manually if title, description, image, or price are missing.
- Try a canonical product URL rather than a search, cart, tracking, or redirect URL.
- If the same shop repeatedly fails, add it to the scraper regression backlog before changing extraction rules.

### A shared list is invisible to another local user

- Confirm the exact username used in the internal share.
- Confirm the recipient is logged in with that local account, not using a public guest link.
- Confirm the recipient account is active.
- Revoke and recreate the share if the permission is wrong.
- Remember that read-only shares cannot edit items; use `CONTRIBUTE` when collaborative item work is intended.

### A public link stopped working

- Public links stop working after revocation.
- Creating a new public link creates a new token; old copied URLs remain invalid.
- Guest claims/signups work only when the public link mode matches the list type: `WISH_CLAIM` for wishlists or `SIGNUP` for non-wishlist signup sheets. `VIEW` links are read-only.
- A claimed item cannot be claimed again; duplicate guest claims return a conflict.

### Reminders do not appear

- Reminder scans cover TODO item due dates, CHORE item due dates, EVENT target dates, and EVENT item due dates.
- Done items do not trigger due-date reminders.
- Recurring chores stay open by advancing to their next due date after Done or Skip.
- If SMTP is complete, reminders can go by email; otherwise in-app notifications are created.
- The default scan is daily at server-local 08:00, so a reminder may not appear immediately after editing a due date.

## Current limitations

- Public guest mutation is intentionally narrow: `WISH_CLAIM` only reserves wishlist items, `SIGNUP` only reserves non-wishlist items, and `VIEW` does not allow guest changes.
- Grocery lists support quantity/category, category grouping, done/reopen, hide-completed, and clear-completed flows, but not offline-first live collaborative shopping.
- Item review controls are client-side; very large lists may later need backend query parameters.
- Chore recurrence supports daily, weekly, biweekly, monthly, quarterly, and annual flows; broader calendar-style recurrence remains a future extension.
- Email features require SMTP configuration; otherwise the app stays functional with password login and in-app notifications.
- Admin inventory is metadata-focused and does not include a break-glass private content view.
- Internet-facing deployment still needs a dedicated HTTPS/HSTS/secure-cookie pass; the current Alice deployment is private Tailnet HTTP.
