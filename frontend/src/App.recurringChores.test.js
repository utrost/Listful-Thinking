import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('recurring chore UI', () => {
  const app = readFileSync('src/App.vue', 'utf8');
  const client = readFileSync('src/api/client.ts', 'utf8');

  it('uses a recurrence picker and exposes skip/postpone controls for chore items', () => {
    expect(app).toContain('recurrenceOptions');
    expect(app).toContain('items.skipOccurrence');
    expect(app).toContain('items.postpone');
    expect(app).toContain('handleSkipChore');
    expect(app).toContain('handlePostponeChore');
    expect(app).toContain('lastCompletedAt');
  });

  it('wires skip and postpone through the API client', () => {
    expect(client).toContain('skipChoreItem');
    expect(client).toContain('/api/v1/items/${itemId}/skip');
    expect(client).toContain('postponeChoreItem');
    expect(client).toContain('/api/v1/items/${itemId}/postpone');
  });
});
