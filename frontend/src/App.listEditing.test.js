import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('list editing and cleanup UI', () => {
  const app = readFileSync('src/App.vue', 'utf8');

  it('renders an owner list edit form wired to updateList', () => {
    expect(app).toContain('updateList');
    expect(app).toContain('editingList');
    expect(app).toContain('handleStartEditList');
    expect(app).toContain('handleSaveEditedList');
    expect(app).toContain("t('lists.edit')");
    expect(app).toContain("t('lists.save')");
    expect(app).toContain("t('lists.cancel')");
  });

  it('requires a confirmation step before destructive list deletion', () => {
    expect(app).toContain('pendingDeleteListId');
    expect(app).toContain('handleRequestDeleteList');
    expect(app).toContain('handleConfirmDeleteList');
    expect(app).toContain("t('lists.deleteConfirm')");
    expect(app).toContain("t('lists.deleteCancel')");
  });

  it('refreshes the selected public share mode after saving list type edits', () => {
    expect(app).toContain('publicShareMode.value = updated.publicShareMode');
    expect(app.indexOf('selectedList.value = updated')).toBeLessThan(app.indexOf('publicShareMode.value = updated.publicShareMode'));
  });
});
