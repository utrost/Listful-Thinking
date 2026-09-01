import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('sharing permission UI', () => {
  const app = readFileSync('src/App.vue', 'utf8');

  it('lets owners choose read-only or contributor access', () => {
    expect(app).toContain('shareForm.permission');
    expect(app).toContain('CONTRIBUTE');
    expect(app).toContain("t('sharing.readOnly')");
    expect(app).toContain("t('sharing.contribute')");
  });
});
