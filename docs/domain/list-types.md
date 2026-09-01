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

UI behavior:

- Show description, URL, image, price, and status.
- Allow URL-only item creation and metadata-prefilled item forms; users can edit fetched values before saving.
- Public guest page supports claiming.

MVP rules:

- Guests can claim `OPEN` wishlist items.
- Owner controls `PURCHASED` status.

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

- Public guest claiming is not a primary CHORE use case.
- Recurrence rule may be stored before full automatic recurrence expansion exists.
- Shopping fields (`url`, `image_url`, `price`) are rejected by the API for chore items.
- Must not set list-level `target_date`; use item `due_date` instead.

Future extension:

- Assign chores to registered users.
- Auto-create next due date when completed.

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
- Public guest claiming is not the primary EVENT behavior.

## Shared validation principles

- Required fields should be validated by API DTOs, not only frontend forms.
- Frontend hides irrelevant fields, but backend remains the authority.
- Unknown future list types should not be added until domain behavior is documented first.