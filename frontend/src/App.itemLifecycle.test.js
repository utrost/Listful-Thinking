import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('item lifecycle UI', () => {
  const app = readFileSync('src/App.vue', 'utf8');

  it('renders item edit and done/undone lifecycle controls', () => {
    expect(app).toContain('editingItemId');
    expect(app).toContain('handleStartEditItem');
    expect(app).toContain('handleSaveEditedItem');
    expect(app).toContain('handleToggleItemDone');
    expect(app).toContain("t('items.edit')");
    expect(app).toContain("t('items.save')");
    expect(app).toContain("t('items.done')");
    expect(app).toContain("t('items.reopen')");
  });
});
