import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('list cloning UI', () => {
  const app = readFileSync('src/App.vue', 'utf8');

  it('renders an owner-only duplicate action wired to cloneList', () => {
    expect(app).toContain('cloneList');
    expect(app).toContain('handleCloneList');
    expect(app).toContain("t('lists.duplicate')");
    expect(app).toContain("selectedList.access === 'OWNER'");
  });
});
