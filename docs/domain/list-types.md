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
- `price_currency`, `price_checked_at`, `variant_note`, `seller`, `availability`, and `source_url`
- `status`
- `reserved_by_guest`

Status meanings:

- `OPEN`: available gift idea.
- `CLAIMED`: reserved by a public guest.
- `PURCHASED`: bought/handled by the owner.

UI behavior:

- Show description, URL, image, price, and status.
- Allow URL-only item creation and metadata-prefilled item forms; users can edit fetched values before saving.
- The separate website capture flow shows a reviewed product draft and warns about a matching product URL in the destination list. The warning is advisory.
- Public guest page supports claiming when the link mode is `WISH_CLAIM`; `VIEW` remains read-only.
- Guests can correct or release their own reservation with the token returned when they claim; the list owner can release a mistaken reservation. Guest names are hidden from owner and other public wishlist reads.

MVP rules:

- Guests can claim `OPEN` wishlist items.
- Owners control wishlist status: `OPEN` or `CLAIMED` can be marked `PURCHASED`, and `PURCHASED` can be reopened to `OPEN`.
- Contributors can edit wishlist item details but cannot change wishlist status.

## TODO

Purpose:

- One-off personal tasks and follow-ups.
- Lightweight reminders for things that should happen at a date/time but are not recurring chores and do not belong to a dated event plan.

Relevant item fields:

- `name`
- `due_date`
- `status`
- optional `owner_label`
- optional `assistant_labels`
- optional `source_url` for a how-to page that supplied a reviewed step

UI behavior:

- Hide price, image, product URL, and recurrence fields. A separate source URL may link a captured how-to page.
- Show due date/time so the reminder scanner can notify the list owner.
- Show owner and assistant/helper labels for coordination without assuming a concrete family structure.

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
- optional `source_url` for a recipe page that supplied a reviewed ingredient

UI behavior:

- Hide product URL, image, price, due-date, and recurrence fields. A separate source URL can identify the recipe.
- Show quantity and category inputs for shop/aisle grouping.
- A recipe page can suggest ingredients as editable lines. Each saved grocery item retains the source URL.

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
- optional `owner_label`
- optional `assistant_labels`
- optional `source_url` for a captured how-to page

UI behavior:

- Hide price, image, and product URL fields by default. A separate source URL may identify a captured how-to page.
- Show due date, recurrence, owner, and assistant/helper labels.

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

- Planning around a deadline such as birthdays, parties, or school events. A shared pool of places for a city trip uses `TRIP`.

Relevant list fields:

- `target_date`
- optional `source_url` and `event_location`

Relevant item fields:

- `name`
- `due_date`
- optional `status`
- optional `owner_label`
- optional `assistant_labels`
- optional `source_url` for a page that supplied a preparation task

UI behavior:

- Require or strongly prompt for target date.
- Emphasize due dates, completion state, owner, and assistant/helper labels.
- Website event capture can prefill the target date and location and add reviewed preparation tasks.
- Hide shopping fields unless the event item is explicitly a wishlist-like item in a future extension.

MVP rules:

- Event lists require `target_date`.
- `event_location` is valid only for `EVENT` lists; any list `source_url` must be HTTP(S).
- The `target_date` appears in Today & upcoming; reminder delivery uses event item `due_date`, not the list target date alone.
- Event items reject shopping fields and recurrence rules in the MVP.
- Public guest claiming is not the primary EVENT behavior, but a `SIGNUP` public link can turn event preparation items into guest-visible signup slots.
- Event items use `OPEN` and `DONE`; wishlist statuses are rejected.
- `DONE` event items are excluded from item due-date reminder scans.

## TRIP

Purpose:

- A shared shortlist of places a group may visit on a city trip.
- Each contributor uses a registered account; the owner grants `CONTRIBUTE` access by username.

Relevant item fields:

- `name` for the place, `description` for a personal note, optional `category`
- `url`, `source_url`, and `image_url` from a reviewed website or manual entry
- `suggested_by_name`, set by the server from the account that created the item
- optional `address`, `latitude`, and `longitude` for a reviewed map pin
- `status` (`OPEN` or `DONE`; `DONE` can mean visited)

UI behavior:

- Pasting a place URL into a trip list opens website review with that list selected. The contributor checks the suggested title, note, address, coordinates, and image before saving one place.
- A matching normalized source URL produces an advisory duplicate warning.
- Items show who suggested them and link back to the place page. Map view plots places with coordinates; places without them remain in List view. The owner can invite registered contributors; read-only members can browse but cannot add places.
- Public links are view-only; anyone holding the link can see addresses and exact map pins. Guest signup and account-free contributions are unavailable for trip lists.

MVP rules:

- Trip lists have no required date. Use an `EVENT` list for dated plans and preparation tasks.
- Product price, seller, availability, quantity, due date, recurrence, and responsibility fields are rejected for trip items.
- There is no route optimization, voting, schedule, address geocoding, or automatic page synchronization.

## LOCATION

Purpose:

- A personal or shared collection of places that does not need a trip itinerary.
- Uses the same reviewed website capture, address, coordinates, map, duplicate warning, and contributor attribution as `TRIP`.

Rules:

- `address` is at most 500 characters. Latitude and longitude must be supplied together, be finite, and fall within ±90 and ±180 degrees.
- Product prices and seller fields, due dates, recurrence, quantity, and responsibility fields are rejected. Category is allowed.
- Public links are view-only and expose addresses and exact map pins to anyone holding the link; registered contributors can add and edit places when granted `CONTRIBUTE` access.
- Map tiles load only after a member opens Map. Address-only places are not geocoded or pinned automatically.

## Shared validation principles

- Required fields should be validated by API DTOs, not only frontend forms.
- Frontend hides irrelevant fields, but backend remains the authority.
- Unknown future list types should not be added until domain behavior is documented first.
