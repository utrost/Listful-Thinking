# Frontend Architecture

## Framework

The frontend uses Vue 3 with Composition API, Vite, and TypeScript.

## i18n

- Use `vue-i18n`.
- Locale files live in `frontend/src/locales/`.
- Browser language auto-detects German (`de`) and falls back to English (`en`).
- All visible strings should be locale keys, not hard-coded component text.

## API access

- Central API client lives under `frontend/src/api/`.
- Use `fetch` with `credentials: "include"` for session cookies.
- Convert backend error shapes into user-visible localized messages.

## Suggested structure

```text
frontend/src/
  api/
  components/
  stores/
  views/
  locales/
  types.ts
```

## State management

MVP can use lightweight composables/stores instead of adding Pinia immediately.

Suggested stores:

- `session`
- `notifications`

Add Pinia only when cross-screen state becomes awkward.

## View model

Initial views:

- Login/register.
- List dashboard.
- List detail.
- Public share page.
- Admin settings.

## Type-specific UI

The list detail screen should branch by list type:

- `WISH`: URL/image/price/status/claim information.
- `CHORE`: due date and recurrence.
- `EVENT`: target date and due dates.

The backend remains the authority for validation even when fields are hidden in the UI.