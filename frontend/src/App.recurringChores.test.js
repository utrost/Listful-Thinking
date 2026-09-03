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

  it('offers every supported recurrence rule in the picker', () => {
    for (const rule of ['FREQ=DAILY', 'FREQ=WEEKLY', 'FREQ=BIWEEKLY', 'FREQ=MONTHLY', 'FREQ=QUARTERLY', 'FREQ=ANNUALLY']) {
      expect(app).toContain(rule);
    }
    for (const key of ['items.daily', 'items.weekly', 'items.biweekly', 'items.monthly', 'items.quarterly', 'items.annually']) {
      expect(app).toContain(key);
    }
  });

  it('wires skip and postpone through the API client', () => {
    expect(client).toContain('skipChoreItem');
    expect(client).toContain('/api/v1/items/${itemId}/skip');
    expect(client).toContain('postponeChoreItem');
    expect(client).toContain('/api/v1/items/${itemId}/postpone');
  });
});
