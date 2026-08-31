import { describe, expect, it } from 'vitest';
import en from './locales/en.json';
import de from './locales/de.json';

describe('locales', () => {
  it('ships English and German app titles', () => {
    expect(en.app.title).toBe('Listful Thinking');
    expect(de.app.title).toBe('Listful Thinking');
  });
});
