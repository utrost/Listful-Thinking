# Use Cases and Story Backlog

This document separates what the app supports today from plausible future directions for a similar self-hosted family/household list application. User stories that define the current MVP also live in `user-stories.md`; acceptance-level checks live in `acceptance-criteria.md`.

## Product frame

Listful Thinking is for small trusted groups: households, families, clubs, workshops, and self-hosters who want private-by-default lists with deliberate sharing. It is not trying to become a generic enterprise project-management suite, marketplace, or calendar replacement.

## Implemented use cases

### 1. Self-hosted household list hub

**Actor:** instance admin / self-hoster

**Goal:** run one private list service with a single data volume and no mandatory external services.

**Current flow:**

1. Start the Docker container.
2. Register the first account; it becomes `ADMIN`.
3. Keep registration disabled by default.
4. Back up the SQLite data volume under `/app/data`.

**User stories:**

- As a self-hoster, I want the app to start from one container and one data volume so I can operate it without a platform stack.
- As a self-hoster, I want the first account to become admin so I do not need a separate bootstrap script.
- As an admin, I want registration disabled after bootstrap so unknown people cannot join my private instance.

### 2. Admin-controlled local user management

**Actor:** instance admin

**Goal:** manage a small private user base without relying on open registration.

**Current flow:**

1. Admin opens the admin panel.
2. Admin creates a user with username, optional email, password, and role.
3. Admin can activate or deactivate existing users.
4. Admin can inspect users and the instance-wide list inventory with owner metadata.

**User stories:**

- As an admin, I want to create a user myself so family members can get accounts while public registration remains closed.
- As an admin, I want to create another admin deliberately so instance maintenance is not tied to one person.
- As an admin, I want to deactivate a user so old accounts can no longer log in or consume email auth links.
- As an admin, I want to see all users with role, email, active state, and creation time so I can maintain the instance.
- As an admin, I want to see all lists with their owner metadata so I can spot abandoned or misplaced lists without opening private item details.

### 3. Password, magic-link, and reset email access

**Actor:** registered user / admin

**Goal:** keep account access usable even when people forget passwords or prefer link-based login.

**Current flow:**

1. User can log in with username/password.
2. User can request a magic login link by email.
3. User can request a password reset email.
4. Tokens are one-time and time-limited.
5. If SMTP is not configured, the feature remains wired but outbound delivery is unavailable.

**User stories:**

- As a user with an email address, I want to receive a magic login link so I can sign in without remembering my password.
- As a user, I want to reset a forgotten password through email so I do not need admin database access.
- As an admin, I want email-based auth to be optional so the app still works in zero-config mode.
- As an admin, I want deactivated accounts blocked from token flows so old email links cannot re-open access.

### 4. Private typed lists

**Actor:** registered user

**Goal:** create lists whose fields match the job: wishlists, to-dos, groceries, chores, and event planning.

**Current flow:**

1. User creates a typed list: `WISH`, `TODO`, `GROCERY`, `CHORE`, or `EVENT`.
2. User adds items with type-appropriate fields.
3. User sees owned lists and lists explicitly shared with them.
4. Other users cannot access private lists by guessing IDs.

**User stories:**

- As a user, I want separate list types so I do not see shopping fields when I am planning chores or tasks.
- As a list owner, I want my private lists isolated from other accounts so household details stay private.
- As a list owner, I want to edit or delete my own lists and items so the app stays tidy.

### 5. Gift wishlist with public claiming

**Actor:** list owner / guest

**Goal:** share gift ideas without forcing guests to make accounts and avoid duplicate purchases.

**Current flow:**

1. Owner creates a `WISH` list.
2. Owner adds items manually or by pasting product URLs.
3. Owner generates a public share link.
4. Guest opens the link without login.
5. Guest claims an open item with their name.
6. Owner can revoke the public link.

**User stories:**

- As a wishlist owner, I want to paste a product URL so title, description, image, and price can be filled automatically when possible.
- As a wishlist owner, I want a public link so relatives or friends can use the list without accounts.
- As a guest, I want to claim an item with my name so other guests do not buy the same thing.
- As a list owner, I want to revoke a public link so old links stop exposing the list.

### 6. Personal to-do list with due-date reminders

**Actor:** registered user

**Goal:** track one-off tasks that need a date/time reminder but are not recurring chores or event plans.

**Current flow:**

1. User creates a `TODO` list.
2. User adds a task with an optional due date/time.
3. Reminder scanning detects upcoming due tasks.
4. In-app notifications are created if email is not configured; email is sent when SMTP is complete.

**User stories:**

- As a user, I want a lightweight to-do template so personal follow-ups are not mixed into chores or events.
- As a user, I want item-level due dates so each task can remind me at the right time.
- As a user, I want reminders to fall back to in-app notifications so the feature works without mail setup.

### 7. Chore list with recurring household work

**Actor:** household member / list owner

**Goal:** track recurring and due household work separately from shopping and gifts.

**Current flow:**

1. User creates a `CHORE` list.
2. User adds tasks with due date and recurrence rule fields.
3. Reminder scanning can notify about due items.
4. Shared registered users can read the list.

**User stories:**

- As a household member, I want chores to show due and recurrence fields so repeated work is visible.
- As a list owner, I want chore lists to hide shopping fields so the form stays focused.
- As a household member, I want reminders before due chores so routine work does not disappear.

### 8. Event preparation checklist

**Actor:** event planner / family member

**Goal:** coordinate tasks that lead up to a dated event.

**Current flow:**

1. User creates an `EVENT` list with a target date.
2. User adds preparation items with optional due dates.
3. Reminder scanning can detect upcoming event targets and due items.
4. Owner can share the list with registered users.

**User stories:**

- As an event planner, I want a target date on the list so the whole checklist has a deadline.
- As an event planner, I want item-level due dates so preparation can be spread over time.
- As a family member, I want access to shared event lists so I can see what still needs doing.

### 9. Internal registered-user sharing

**Actor:** list owner / internal shared user

**Goal:** let trusted local users view or contribute to a list without granting ownership.

**Current flow:**

1. Owner shares a list with another registered user by username.
2. Owner chooses read-only or contributor permission.
3. Recipient sees the shared list in their account.
4. Read-only recipients can view the list and items.
5. Contributor recipients can add, edit, complete, and reopen items where permitted.
6. Owner can revoke the share.

**User stories:**

- As a list owner, I want to share a list with a local user so household members can see it in their own login.
- As a list owner, I want to grant contributor access deliberately when a trusted user should help work the list.
- As a recipient, I want shared lists visible beside my own lists so I do not need public links inside the household.
- As a list owner, I want to revoke internal sharing so accidental access can be cleaned up.

### 10. Grocery shop mode

**Actor:** grocery list owner / contributor

**Goal:** use a grocery list in the shop without constantly editing text.

**Current flow:**

1. User creates a `GROCERY` list.
2. User adds items with optional quantity and category.
3. The list groups items by category.
4. User marks items Done/Reopen while shopping.
5. Owner can hide completed items and clear completed grocery items after a trip.

**User stories:**

- As a shopper, I want items grouped by category so the in-store pass is faster.
- As a shopper, I want one-tap done/reopen controls so the list works on a phone.
- As a list owner, I want to clear completed grocery items so the next shopping trip starts cleanly.

### 11. Reusable list duplication

**Actor:** list owner

**Goal:** reuse a known-good list without copying public tokens or internal shares by accident.

**Current flow:**

1. Owner duplicates an existing list.
2. The app copies list metadata and items with new IDs.
3. The clone stays owner-isolated and does not copy public tokens or internal shares.

**User stories:**

- As a traveler, I want to duplicate a packing list so I can start from a proven list for each trip.
- As a household member, I want to duplicate a grocery or event list so recurring real-life plans do not start from a blank page.
- As a list owner, I want cloned lists to stay private until I deliberately share them.

## Brainstorm: common adjacent use cases

These are not commitments. They are candidate directions for Listful Thinking or a similar app. Keep the MVP small; promote only the stories that match real household usage.

### A. Shared grocery and errand lists

**Why common:** groceries are the everyday family list case; people need fast add/check-off flows more than project-management features.

**Candidate stories:**

- As a household member, I want a grocery template so item entry is optimized for quantities and categories.
- As a shopper, I want items grouped or labeled by store aisle/category so the in-store pass is faster.
- As a household member, I want recently bought items suggested so repeated staples are easy to re-add.
- As a household member, I want multiple people to check off grocery items live so parallel shopping does not duplicate work.

### B. Packing lists and travel preparation

**Why common:** reusable checklists with per-trip deadlines fit between to-do and event lists.

**Candidate stories:**

- As a traveler, I want a reusable packing template so I can start from a proven list for each trip.
- As a parent, I want child-specific packing sections so school trips and family travel are not forgotten.
- As a traveler, I want items marked as reusable defaults versus trip-specific additions so templates stay clean.
- As a traveler, I want a final “leaving home” checklist so last-minute tasks are visible on the travel day.

### C. Borrowing, lending, and inventory

**Why common:** households and clubs lend tools, books, camera gear, games, and equipment; lists need custody and return dates.

**Candidate stories:**

- As an owner, I want to record who borrowed an item and when it should return so things do not vanish.
- As a borrower, I want reminders before the return date so I can bring items back on time.
- As a club admin, I want a simple equipment inventory so members know what exists before buying duplicates.
- As an owner, I want photos and serial notes on valuable items so identification is easy.

### D. Meal planning and recipe shopping

**Why common:** meal plans generate shopping lists and calendar-adjacent reminders.

**Candidate stories:**

- As a household member, I want a weekly meal plan so everyone knows what is planned for dinner.
- As a cook, I want recipes to generate grocery items so planning turns into a shopping list.
- As a household member, I want dietary notes visible so meals avoid allergies or disliked ingredients.
- As a cook, I want pantry staples separated from things to buy so the list does not over-order basics.

### E. Maintenance schedules

**Why common:** cars, bikes, printers, filters, plants, and appliances have recurring but low-frequency tasks.

**Candidate stories:**

- As a homeowner, I want recurring maintenance lists so annual or quarterly work is not forgotten.
- As a bike owner, I want mileage- or date-based maintenance reminders so service happens before failure.
- As a household member, I want maintenance history so I know when something was last done.
- As an admin, I want maintenance templates for common home systems so setup is quick.

### F. School, family, and care coordination

**Why common:** families coordinate forms, appointments, purchases, and deadlines across multiple people.

**Candidate stories:**

- As a parent, I want school deadlines collected in one list so forms, payments, and materials are not missed.
- As a caregiver, I want recurring medication or appointment preparation reminders so care tasks are reliable.
- As a household member, I want responsibility assignment so everyone sees who is handling each task.
- As a parent, I want gentle notification digests instead of one alert per tiny task.

### G. Hobby project build lists

**Why common:** workshops need parts, steps, materials, and source links; this app already has URL metadata and typed lists.

**Candidate stories:**

- As a maker, I want a project list that combines parts, tasks, and reference links so a build stays organized.
- As a maker, I want item status such as `needed`, `ordered`, `arrived`, and `installed` so procurement is visible.
- As a maker, I want external URLs enriched with title/image so parts lists are readable later.
- As a collaborator, I want read-only project links so I can review the plan without editing it.

### H. Club or small-group signup sheets

**Why common:** non-enterprise groups need “who brings what / who does what” without accounts for everyone.

**Candidate stories:**

- As an organizer, I want a public signup link so guests can reserve tasks or items without accounts.
- As a guest, I want to claim a slot with my name and contact note so organizers can follow up.
- As an organizer, I want limits per slot so too many people cannot claim the same role.
- As an organizer, I want to close a signup link after the event so stale links stop collecting claims.

### I. Decision and shortlist lists

**Why common:** families compare products, trips, schools, restaurants, or vendors and need structured options rather than tasks.

**Candidate stories:**

- As a household member, I want a shortlist template so options can have notes, links, prices, and pros/cons.
- As a group, I want simple voting or ranking so preferences are visible without a meeting.
- As a list owner, I want a decision state such as `candidate`, `rejected`, `chosen` so the list converges.
- As a self-hoster, I want decisions to remain private instead of living in a third-party poll service.

### J. Subscription and renewal tracking

**Why common:** households forget trial endings, renewals, insurance, domains, and contracts.

**Candidate stories:**

- As a household member, I want renewal reminders so subscriptions do not silently renew.
- As an admin, I want cost fields and cadence so monthly/yearly expenses can be reviewed.
- As a user, I want cancellation instructions or contract links attached so acting on a reminder is easy.
- As a household member, I want a yearly renewal overview so budget surprises are visible.

## Candidate prioritization lens

Promote a brainstormed use case when it satisfies most of these:

- It reuses the existing list/item/share/reminder model.
- It benefits from privacy-by-default self-hosting.
- It fits small trusted groups rather than enterprise workflows.
- It can degrade gracefully without email or third-party APIs.
- It can be introduced as a typed template without turning the app into a generic project manager.
- It has a real “guest link” or “household sharing” advantage over a plain notes file.

## Near-term story candidates

These are the most natural next candidates from the brainstorm because they extend current features rather than requiring a new product shape.

1. **Grocery/errand list polish** — quantity/category fields, grouping, one-tap done/reopen, hide completed, and clear completed exist; future work is staple suggestions and optional live collaboration.
2. **Reusable packing/event templates** — basic safe list duplication exists; future work is explicit template management and trip-specific/default item handling.
3. **Maintenance schedule list** — simple daily/weekly/monthly chore recurrence exists; future work is maintenance history, mileage-based reminders, and specialized templates.
4. **Signup sheet mode for public links** — public guests can claim wishlist items today; future work is claimable non-wishlist slots/tasks.
5. **Project parts list** — wishlist-like URL enrichment plus task-like status for hobby builds.
6. **Renewal tracker** — due-date reminders plus cost/cadence metadata.

## Product decisions still open

These are the remaining product choices that need real-world direction before they become implementation slices:

- Should public links stay gift-wishlist-only, or should they become a generic claim/reserve/signup mechanism?
- Should reusable templates be explicit user-managed templates, hard-coded starter templates, or just duplicate-list workflows for now?
- Should reminders remain in-app/email, or eventually integrate with calendars, webhooks, or mobile push?
- Should admin list visibility remain metadata-only, or should admins have a break-glass support view with audit logging?
- Which adjacent use case should be promoted first: maintenance, project parts, renewal tracking, meal planning, borrowing/inventory, school/care coordination, or decision shortlists?
