# Frontend Architecture

The frontend uses Vue 3, TypeScript, Vite, and vue-i18n. The backend serves the compiled app from the same origin as /api/v1.

## Current structure

- frontend/src/App.vue coordinates authentication, list selection, Items/Sharing/List settings, notifications, and account actions.
- components/AdminPanel.vue handles instance settings, users, setup/recovery links, and inactive-owner transfers. PlanningHub.vue presents Today & upcoming; PrivateImage.vue protects remote image loading.
- api/client.ts contains typed API calls and maps backend errors. Session requests include cookies; authenticated browser mutations fetch and send X-CSRF-TOKEN.
- listTypes.ts defines type-specific form rules; itemReview.ts handles list-local search, filtering, and sorting.
- locales/en.json and locales/de.json supply visible copy. Browser German is detected and English is the fallback; chosen language and last selected list are remembered locally.

## Access and workflow boundaries

The UI hides owner-only controls from shared readers and contributors, but the backend remains the authorization authority. Owners can invite registered users, manage public links, and transfer ownership. An admin can manage accounts and transfer all lists from an inactive owner while seeing only per-user list counts. Setup links are displayed once if mail does not deliver them; users set their own password on the reset page.

Password change, setup/recovery, login, and logout refresh the local session/CSRF state. The UI presents backend conflicts rather than silently overwriting stale item edits. Deleted lists/items enter Trash, while public links and guest actions remain a separate unauthenticated view.

## Validation

Vitest covers API, list rules, components, and rendered workflows. Playwright runs desktop and mobile Chromium journeys against an isolated Docker stack, including account setup, recovery, admin and user processes, sharing, and owner handoffs. Use disposable test data; never point the GUI suite at the live Alice instance.
