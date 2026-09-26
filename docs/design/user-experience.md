# Listful Thinking: user experience concept

## Product promise
A calm place to keep wishes, plans, and everyday lists. The interface should help a person answer: Where am I? What needs my attention? What can I do next?

## Information architecture
- A compact brand header with the signed-in name, language, and account actions.
- Your lists: persistent list navigation, searchable when needed, with a clear New list action. On phones this becomes a compact list selector.
- Selected list: title, type, description, open/total count, and access information. Items is the default view. Sharing and List settings are separate destinations for owners.
- Administration: a separate account destination, with a return-to-lists action. No account-management forms above someone's wishes or shopping list.
- Public list: a welcoming, read-only or reservation view with a short explanation of what visitors can do.
- Authentication: sign in first; email recovery and account creation are secondary, explained routes.

## Core journeys
1. Returning user: sign in → current list → read, add, or complete an item. Avoid a large marketing header after login.
2. First list: friendly empty state → New list → choose a purpose → name it → Create list. Explain the five purposes and any required event date.
3. Add an item: name (or product URL for wishes), then Add item. Product metadata can populate asynchronously. Description, image and price belong in optional details. Groceries keep quantity visible; chores and events keep their relevant dates/responsibility available with persistent labels.
4. Review: item content owns the row; primary completion action stays visible. Editing and destructive actions remain secondary. Search is visible; filter/sort options can be expanded. Explain zero search results and offer a reset.
5. Share: open Sharing → choose a public-link mode or invite an existing user. Explain read-only versus contribution and public access. Show copy/open actions when the link is created. Retain replacement warnings and confirmation flows.
6. Manage a list: open List settings → edit title/details, duplicate, or explicitly confirm deletion. Explain why a populated list's type cannot change.
7. Use a phone: select a list without a long grid of list cards; primary controls and items fit the viewport. Touch targets are at least 44px. Long titles, URLs and translations wrap.

## Visual language
Warm off-white background, white work surfaces, deep evergreen primary actions, restrained gold accents. A compact, legible system font, clear heading levels, consistent spacing and rounded rectangular controls. Avoid nested cards around every section and red delete buttons dominating each row. Use visible text instead of unexplained icons. No decorative stock imagery is needed.

## Interaction and accessibility
Persistent labels; semantic navigation; active-page states; keyboard focus rings; errors announced as alerts; success announced as status. Native disclosure controls for optional detail fields and secondary row actions. Separate forms so recovery never validates login fields. Preserve drafts when opening another view; reset per-list editing state when selecting a different list. Read-only access shows an explicit explanation and no editing controls. Essential guidance is localized in English and German.

## Validation and iteration
Use disposable Docker data, never change live lists to test the design. Run existing unit tests and production build. Playwright covers first-use onboarding, each list purpose, optional fields, search/no-results/reset, completion, edit/settings/delete confirmation, sharing/guest reservation, read-only access, admin navigation, authentication, desktop and mobile layouts. Capture screenshots at desktop and phone widths, review the hierarchy and density, then fix visual or interaction failures and rerun the affected checks.

## Scope
This revision changes the interface and user guidance while retaining existing API behavior and list capabilities. Drag-and-drop ordering, cross-list dashboards, collaboration notifications and new account settings are future product work.

## Implemented revision and iteration record — 2026-09-26
The implementation uses a desktop list rail and a phone list selector, Items/Sharing/List settings navigation, a separate Administration destination, purpose-specific copy, guided empty states, expandable item forms and row actions, and English/German language selection. The browser remembers the selected language and last list per account. Recovery is a secondary sign-in route; reset links open a dedicated new-password form and return to sign-in with a confirmation.

Visual/browser review led to four refinements: feedback no longer overlays the form; optional details and the add form close after a successful addition; phone selectors have explicit accessible names; deleting a list returns the next list to Items rather than leaving it in Settings.

Validation: production Docker frontend build passed; 63 frontend tests passed; the full Playwright run passed 14 active desktop/mobile journeys (6 deliberate duplicate-platform skips). After the final deletion-navigation fix, both desktop and mobile focused journeys passed again. Browser checks cover zero horizontal overflow, read-only/contributor access, guest reservations, new-user guidance, search/reset, all five list purposes, settings/edit/delete confirmation, language and list continuity after reload, and focused recovery presentation. The reset-page UI test intercepts its consume endpoint; token semantics and real SMTP behavior were verified separately in the authentication work.
