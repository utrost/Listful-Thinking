import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('item review controls UI', () => {
  const app = readFileSync('src/App.vue', 'utf8');

  function functionBody(name) {
    const match = app.match(new RegExp(`async function ${name}\\([^)]*\\) \\{([\\s\\S]*?)\\n\\}`));
    expect(match, `${name} function exists`).not.toBeNull();
    return match?.[1] ?? '';
  }

  it('renders search, filter, and sort controls for selected list items', () => {
    expect(app).toContain('itemReviewForm.query');
    expect(app).toContain('itemReviewForm.statusFilter');
    expect(app).toContain('itemReviewForm.sortBy');
    expect(app).toContain("t('items.search')");
    expect(app).toContain("t('items.filter')");
    expect(app).toContain("t('items.sort')");
    expect(app).toContain('reviewDisplayedItems(');
  });

  it('localizes the item review controls aria label', () => {
    expect(app).toContain(':aria-label="t(\'items.reviewControlsLabel\')"');
    expect(readFileSync('src/locales/en.json', 'utf8')).toContain('"reviewControlsLabel"');
    expect(readFileSync('src/locales/de.json', 'utf8')).toContain('"reviewControlsLabel"');
  });

  it('resets review controls on explicit list switches and feeds a reactive clock to review helpers', () => {
    const selectListBody = functionBody('selectList');
    expect(selectListBody).toContain('resetItemReviewForm()');
    expect(selectListBody).toContain('updateItemReviewNow()');
    expect(app).toContain('itemReviewNow');
    expect(app).toContain('window.setInterval(updateItemReviewNow');
    expect(app).toContain('now: itemReviewNow.value');
  });

  it('also resets review controls when loadLists implicitly selects the first list', () => {
    const loadListsBody = functionBody('loadLists');
    expect(loadListsBody).toContain('selectedList.value = lists.value[0] ?? null;');
    expect(loadListsBody).toContain('resetItemReviewForm()');
    expect(loadListsBody).toContain('updateItemReviewNow()');
    expect(loadListsBody).toContain('await loadListDetails();');
  });
});
