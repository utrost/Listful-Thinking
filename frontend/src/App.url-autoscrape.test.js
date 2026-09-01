import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('item URL grabbing form', () => {
  it('triggers metadata grabbing when a wish URL field changes', () => {
    const app = readFileSync('src/App.vue', 'utf8');

    expect(app).toMatch(/v-model="itemForm\.url"[^>]*@(change|blur)="handleScrapeItemUrl"/);
  });
});
