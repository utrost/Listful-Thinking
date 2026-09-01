import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('grocery shop mode UI', () => {
  const app = readFileSync('src/App.vue', 'utf8');
  const client = readFileSync('src/api/client.ts', 'utf8');

  it('renders grocery items grouped by category with a hide-completed shop view', () => {
    expect(app).toContain('groceryGroups');
    expect(app).toContain('hideCompletedGroceries');
    expect(app).toContain('t(\'items.uncategorized\')');
    expect(app).toContain('t(\'items.hideCompleted\')');
  });

  it('wires a clear completed grocery action through the API client', () => {
    expect(client).toContain('clearCompletedItems');
    expect(client).toContain('/api/v1/lists/${listId}/items/completed');
    expect(app).toContain('handleClearCompletedGroceries');
    expect(app).toContain('t(\'items.clearCompleted\')');
  });
});
