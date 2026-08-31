# User Stories

This document captures intended product behavior before implementation. Acceptance-level details live in `acceptance-criteria.md`.

## Epic: First-run bootstrap

### Story: First user becomes admin

As a self-hoster, I want the first registered account to become admin so the app works without setup scripts.

### Story: Registration disabled by default

As an admin, I want public registration disabled unless enabled so unknown users cannot join my private instance.

## Epic: Authentication

### Story: Login with local account

As a registered user, I want to log in with username/password so I can access my lists.

### Story: Logout clears session

As a registered user, I want logout to end my session so shared devices are safe.

### Story: Current user endpoint

As the frontend, I need to ask who is logged in so the UI can route and show admin options.

## Epic: Private lists

### Story: Create typed list

As a registered user, I want to create a `WISH`, `CHORE`, or `EVENT` list so the UI can show relevant fields.

### Story: Manage my own lists

As an owner, I want to edit and delete my own lists so stale lists can be corrected or removed.

### Story: Isolation from other users

As a registered user, I must not see or mutate another user's private lists so private household data stays private.

## Epic: Items

### Story: Add item manually

As a list owner, I want to add an item with fields appropriate to the list type so the list becomes useful immediately.

### Story: Edit item status

As a list owner, I want to update item status so I can track open, claimed, and purchased entries.

### Story: URL-only wishlist item

As a wishlist owner, I want to paste only a URL so the app can create a placeholder and enrich it from metadata.

## Epic: Internal sharing

### Story: Share list with registered user

As an owner, I want to share a list with another local user so family members can view the same list.

### Story: Revoke internal sharing

As an owner, I want to remove a user's access so sharing remains under my control.

## Epic: Public sharing

### Story: Generate guest link

As an owner, I want to generate a public share link so guests can access one list without an account.

### Story: Revoke guest link

As an owner, I want to revoke a public share link so old links stop working.

### Story: Guest reads safe public list

As a guest, I want to open a share link and see list items so I can decide what to reserve.

### Story: Guest claims item

As a guest, I want to claim an open item with my name so duplicate purchases are avoided.

## Epic: List templates

### Story: Wishlist-specific fields

As a wishlist owner, I want price, URL, and image fields so gift ideas are useful.

### Story: Chore-specific fields

As a chore-list owner, I want recurrence and due dates without shopping fields so household tasks stay clear.

### Story: Event-specific fields

As an event-list owner, I want a target date and due items so event planning has a deadline.

## Epic: Reminders

### Story: In-app reminder fallback

As a user, I want reminders to appear in the app when email is not configured so the feature still works zero-config.

### Story: Email reminders when configured

As an admin, I want SMTP configuration to enable email reminders so users can receive alerts outside the app.

## Epic: Internationalization

### Story: German and English UI

As a user, I want the frontend to use my browser language so the app is comfortable from day one.

### Story: Localized backend messages

As the frontend, I want backend errors localized through `Accept-Language` so users see consistent language.

## Epic: Self-hosting operations

### Story: Single-volume backup

As a self-hoster, I want all mutable app data under `/app/data` so backup is simple.

### Story: Non-root runtime

As a self-hoster, I want the container to run as non-root so deployment follows basic security expectations.