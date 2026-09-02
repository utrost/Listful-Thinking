import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('item review controls UI', () => {
  const app = readFileSync('src/App.vue', 'utf8');

  it('renders search, filter, and sort controls for selected list items', () => {
    expect(app).toContain('itemReviewForm.query');
    expect(app).toContain('itemReviewForm.statusFilter');
    expect(app).toContain('itemReviewForm.sortBy');
    expect(app).toContain("t('items.search')");
    expect(app).toContain("t('items.filter')");
    expect(app).toContain("t('items.sort')");
    expect(app).toContain('reviewItems(');
  });
});
