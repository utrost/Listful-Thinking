# List Types

Lists share a common persistence model but have type-specific product meaning.

## WISH

Purpose:

- Gift ideas, purchase coordination, and public guest claiming.

Relevant list fields:

- `title`
- `description`
- optional public `share_token`

Validation:

- Must not set list-level `target_date`; deadlines belong on items for this type.

Relevant item fields:

- `name`
- `description`
- `url`
- `image_url`
- `price`
- `status`
- `reserved_by_guest`

Status meanings:

- `OPEN`: available gift idea.
- `CLAIMED`: reserved by a public guest.
- `PURCHASED`: bought/handled by the owner.

UI behavior:

- Show description, URL, image, price, and status.
- Allow URL-only item creation and metadata-prefilled item forms; users can edit fetched values before saving.
- Public guest page supports claiming when the link mode is `WISH_CLAIM`; `VIEW` remains read-only.

MVP rules:

- Guests can claim `OPEN` wishlist items.
- Owner controls `PURCHASED` status.

## TODO

Purpose:

- One-off personal tasks and follow-ups.
- Lightweight reminders for things that should happen at a date/time but are not recurring chores and do not belong to a dated event plan.

Relevant item fields:

- `name`
- `due_date`
- `status`

UI behavior:

- Hide price, image, URL, and recurrence fields.
- Show due date/time so the reminder scanner can notify the owner.

MVP rules:

- Must not set list-level `target_date`; use item `due_date` instead.
- Shopping fields (`url`, `image_url`, `price`) are rejected by the API for todo items.
- Recurrence rules are rejected by the API for todo items; recurring work belongs in `CHORE`.
- Todo items with a due date participate in the same notification scan as event/chore items.
- Todo items use `OPEN` and `DONE`; wishlist statuses are rejected.
- `DONE` todo items are excluded from reminder scans.

## GROCERY

Purpose:

- Weekly shopping and errands.
- Fast item entry with practical shop-facing metadata.

Relevant item fields:

- `name`
- `quantity`
- `category`
- `status`

UI behavior:

- Hide URL, image, price, due-date, and recurrence fields.
- Show quantity and category inputs for shop/aisle grouping.

MVP rules:

- Must not set list-level `target_date`.
- Shopping fields (`url`, `image_url`, `price`) are rejected by the API for grocery items.
- Due dates and recurrence rules are rejected by the API for grocery items; dated follow-ups belong in `TODO`/`CHORE`/`EVENT`.
- Public guest claiming is not the primary GROCERY behavior, but a `SIGNUP` public link can be used when grocery-style items are repurposed as open slots/reservations.
- Grocery items use `OPEN` and `DONE`; wishlist statuses are rejected.

## CHORE

Purpose:

- Household tasks and recurring duties.

Relevant item fields:

- `name`
- `due_date`
- `recurrence_rule`
- `status`

UI behavior:

- Hide price, image, and URL fields by default.
- Show due date and recurrence.

MVP rules:

- Public guest claiming is not a primary CHORE use case, but a `SIGNUP` public link can be used for small-group volunteer slots.
- Simple recurrence is supported for daily, weekly, biweekly, monthly, quarterly, and annual chores.
- Shopping fields (`url`, `image_url`, `price`) are rejected by the API for chore items.
- Must not set list-level `target_date`; use item `due_date` instead.
- Chore items use `OPEN` and `DONE`; wishlist statuses are rejected.
- `DONE` chore items are excluded from reminder scans.

Current recurrence behavior:

- Daily, weekly, and biweekly recurrence advance by 1, 7, or 14 days.
- Monthly, quarterly, and annual recurrence use UTC calendar arithmetic, so month-end dates clamp safely when the target month is shorter.
- Completing a recurring chore records `last_completed_at`, advances `due_date`, and returns the item to `OPEN` for the next occurrence.
- Skipping advances to the next occurrence without changing `last_completed_at`; postponing shifts the due date by an explicit number of days without changing the recurrence rule.

## EVENT

Purpose:

- Planning around a deadline such as birthdays, trips, parties, or school events.

Relevant list fields:

- `target_date`

Relevant item fields:

- `name`
- `due_date`
- optional `status`

UI behavior:

- Require or strongly prompt for target date.
- Emphasize due dates and completion state.
- Hide shopping fields unless the event item is explicitly a wishlist-like item in a future extension.

MVP rules:

- Event lists require `target_date`.
- Event lists can trigger reminders based on `target_date` and item `due_date`.
- Event items reject shopping fields and recurrence rules in the MVP.
- Public guest claiming is not the primary EVENT behavior, but a `SIGNUP` public link can turn event preparation items into guest-visible signup slots.
- Event items use `OPEN` and `DONE`; wishlist statuses are rejected.
- `DONE` event items are excluded from item due-date reminder scans.

## Shared validation principles

- Required fields should be validated by API DTOs, not only frontend forms.
- Frontend hides irrelevant fields, but backend remains the authority.
- Unknown future list types should not be added until domain behavior is documented first.