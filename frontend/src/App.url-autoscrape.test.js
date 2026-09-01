import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('item URL grabbing form', () => {
  it('triggers metadata grabbing when a wish URL field changes', () => {
    const app = readFileSync('src/App.vue', 'utf8');

    expect(app).toMatch(/v-model="itemForm\.url"[^>]*@(change|blur)="handleScrapeItemUrl"/);
  });

  it('shows editable fields for fetched title description image and price before creating an item', () => {
    const app = readFileSync('src/App.vue', 'utf8');

    expect(app).toContain('v-model="itemForm.name"');
    expect(app).toContain('v-model="itemForm.description"');
    expect(app).toContain('v-model="itemForm.imageUrl"');
    expect(app).toContain('v-if="itemForm.imageUrl"');
    expect(app).toContain('v-if="item.imageUrl"');
    expect(app).toContain('v-model.number="itemForm.price"');
    expect(app).toContain('itemForm.description = scraped.description');
    expect(app).toContain('description: itemForm.description || undefined');
  });
});
