# Item Responsibility Metadata

This document describes the current owner/responsible and assistant/helper feature as implemented today.

## Purpose

Listful Thinking supports optional responsibility labels on work-style items so small self-hosted groups can coordinate who is accountable for a task and who may help with it.

The model is deliberately generic. A self-hosted instance may represent a family, couple, shared house, care team, club, small volunteer group, or small work team. The product language therefore avoids hardcoded people and avoids assuming that every actor is a family member.

## Fields

Item responsibility is stored on `items` as nullable text fields:

- `owner_label`: user-visible label for the person, role, group, bot, or automation responsible for making sure the item happens.
- `assistant_labels`: user-visible label or comma-separated free-text labels for helpers, assistants, bots, or automations that support the item.

API field names are camelCase:

- `ownerLabel`
- `assistantLabels`

Both fields are optional. Existing items keep `null` values after migration.

## Semantics

### List owner vs item responsibility owner

The registered account that owns a list is still the access-control owner. This is the account that can edit list metadata, delete the list, manage shares, and manage public links.

`ownerLabel` is different. It is item-level coordination metadata. It can say who is responsible for a task inside the household/team context, but it does not change authorization.

Example:

```json
{
  "name": "Prepare guest room",
  "dueDate": "2027-03-12T16:00:00Z",
  "ownerLabel": "Host on duty",
  "assistantLabels": "Cleaning robot, reminder bot"
}
```

### Assistants/helpers

`assistantLabels` records optional supporting actors. Helpers may be humans, roles, software assistants, local automations, or bots. Naming an assistant is not the same as granting access, sending a notification, or configuring automation.

The current slice treats assistants as labels only. Future versions can add structured actors, notification routing, task rotation, or automation integrations without changing the meaning of the current fields.

## Supported list types

The frontend shows responsibility fields for work-style item types:

- `TODO`
- `CHORE`
- `EVENT`

The backend stores the fields at item level so the data model stays simple and migration-safe. UI visibility decides where the fields are normally useful.

`WISH` and `GROCERY` do not currently show these fields in the normal item form. Wishlist gift coordination still uses claim/purchased status; grocery lists focus on quantity, category, and shopping flow.

## Permissions and privacy

Responsibility labels do not grant permissions.

- They do not create a registered user.
- They do not create an internal share.
- They do not create a public link.
- They do not route reminders.
- They do not override owner/contributor/read-only access checks.

Access remains governed by the parent list:

- list owner
- internal `READ` / `CONTRIBUTE` shares
- public link mode (`VIEW`, `WISH_CLAIM`, `SIGNUP`)

Because labels are user-visible item metadata, instance users should avoid putting secrets, passwords, private addresses, or sensitive personal data into them.

## Persistence and migration

Migration `V12__add_item_responsibility_labels.sql` adds:

```sql
ALTER TABLE items ADD COLUMN owner_label TEXT;
ALTER TABLE items ADD COLUMN assistant_labels TEXT;
```

The migration is additive and preserves existing rows. The Alice deploy preflight applied the migration to a copied SQLite database, confirmed the existing item count was preserved, then verified the live database after deployment.

## Validation and limits

The request DTO accepts both fields as optional strings with the same general style of bounded text validation used for item text fields.

Recommended use:

- Keep labels short and readable.
- Prefer names, roles, or automation labels over long instructions.
- Put details in the item description instead of overloading `assistantLabels`.

## Current implementation files

Backend:

- `backend/src/main/resources/db/migration/V12__add_item_responsibility_labels.sql`
- `backend/src/main/java/app/listful/domain/Item.java`
- `backend/src/main/java/app/listful/items/dto/ItemRequest.java`
- `backend/src/main/java/app/listful/items/dto/ItemResponse.java`
- `backend/src/main/java/app/listful/items/ItemService.java`

Frontend:

- `frontend/src/App.vue`
- `frontend/src/api/client.ts`
- `frontend/src/listTypes.ts`
- `frontend/src/locales/en.json`
- `frontend/src/locales/de.json`

Tests and smoke coverage:

- `backend/src/test/java/app/listful/items/ItemControllerTests.java`
- `backend/src/test/java/app/listful/persistence/SchemaMigrationTests.java`
- `frontend/src/App.ownerAssistants.test.js`
- `frontend/src/api/client.test.ts`
- `frontend/src/listTypes.test.ts`
- `scripts/smoke.sh`

## Future-compatible direction

A later structured actor model can add records such as members, roles, assistant agents, notification preferences, rotations, and permissions. That should be a separate design slice. The current labels are intentionally lightweight coordination metadata so the MVP can support real use now without prematurely hardcoding a family model or a bot platform.
