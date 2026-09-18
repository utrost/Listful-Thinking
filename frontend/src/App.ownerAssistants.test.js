import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const app = readFileSync(new URL('./App.vue', import.meta.url), 'utf8');

describe('item owner and assistant labels', () => {
  it('renders create/edit inputs and visible labels without hardcoded household names', () => {
    expect(app).toContain('v-model="itemForm.ownerLabel"');
    expect(app).toContain('v-model="itemForm.assistantLabels"');
    expect(app).toContain('v-model="editItemForm.ownerLabel"');
    expect(app).toContain('v-model="editItemForm.assistantLabels"');
    expect(app).toContain("t('items.ownerLabel')");
    expect(app).toContain("t('items.assistantLabels')");
    expect(app).not.toMatch(/Uwe|Martha|Alice/);
  });

  it('sends responsibility metadata only when the selected list type supports it', () => {
    expect(app).toContain('ownerLabel: fields.showResponsibility ? itemForm.ownerLabel || undefined : undefined');
    expect(app).toContain('assistantLabels: fields.showResponsibility ? itemForm.assistantLabels || undefined : undefined');
    expect(app).toContain('ownerLabel: fields.showResponsibility ? editItemForm.ownerLabel || undefined : undefined');
    expect(app).toContain('assistantLabels: fields.showResponsibility ? editItemForm.assistantLabels || undefined : undefined');
    expect(app).toContain('ownerLabel: item.ownerLabel ?? undefined');
    expect(app).toContain('assistantLabels: item.assistantLabels ?? undefined');
  });
});
