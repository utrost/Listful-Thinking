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
- Share their own lists read-only with another registered user.
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
- `DONE`: completed; due-date reminders stop for this occurrence.

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

For `TODO`, `GROCERY`, `CHORE`, and `EVENT` lists, use **Done** to complete an item and **Reopen** to move it back to `OPEN`. Done items do not create due-date reminders.

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

The shared user can:

- See the shared list.
- Read the list's items.

The shared user cannot, in the current MVP:

- Edit the list.
- Delete the list.
- Add items.
- Edit items.
- Delete items.
- Manage shares.
- Generate or revoke public guest links.

The list owner can revoke the internal share at any time.

### Public guest links

A list owner can create a public link for one list. The link is intended for guests who should not need an account.

Guests can:

- Open the public link without logging in.
- See a safe public version of the list.
- Claim an open wishlist item by entering their name.

Guests cannot:

- Browse other lists.
- See owner email addresses or internal user IDs.
- See internal sharing records.
- See admin data.
- Edit list metadata.
- Add or delete items.
- Claim items from a different list.

Public guest claiming is currently limited to `WISH` lists. A list owner can revoke a public link so the old URL stops working.

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

## Current limitations

- Internal sharing is read-only.
- Public claiming is only for wishlist items.
- Grocery lists support quantity/category and done/reopen, but do not yet have live collaborative check-off or category grouping views.
- Hide-completed and richer filtering are still future roadmap work.
- Recurrence rules can be stored for chores, but full automatic recurrence expansion is still a future extension.
- Email features require SMTP configuration; otherwise the app stays functional with password login and in-app notifications.
- Admin inventory is metadata-focused and does not include a break-glass private content view.
