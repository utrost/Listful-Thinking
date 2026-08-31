import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('admin workspace shell', () => {
  const appVue = readFileSync(new URL('./App.vue', import.meta.url), 'utf8');

  it('gates the admin panel to admin users and wires settings/user management actions', () => {
    expect(appVue).toContain("currentUser.role === 'ADMIN'");
    expect(appVue).toContain("t('admin.title')");
    expect(appVue).toContain('handleLoadAdminPanel');
    expect(appVue).toContain('handleToggleRegistration');
    expect(appVue).toContain('adminUsers');
  });
});
