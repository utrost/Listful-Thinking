import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('public registration visibility', () => {
  const appVue = readFileSync(new URL('./App.vue', import.meta.url), 'utf8');
  const apiClient = readFileSync(new URL('./api/client.ts', import.meta.url), 'utf8');

  it('loads public auth settings before rendering unauthenticated forms', () => {
    expect(apiClient).toContain('registrationAvailable');
    expect(apiClient).toContain('getAuthSettings');
    expect(appVue).toContain('getAuthSettings');
    expect(appVue).toContain('await loadAuthSettings()');
  });

  it('only renders self-registration when public auth settings allow it', () => {
    expect(appVue).toContain('v-if="authSettings?.registrationAvailable ?? false"');
    expect(appVue).toContain('v-else-if="authSettings && !authSettings.registrationAvailable"');
    expect(appVue).toContain("t('auth.registrationDisabled')");
  });
});
