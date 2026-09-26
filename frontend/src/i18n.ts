import { createI18n } from 'vue-i18n';
import en from './locales/en.json';
import de from './locales/de.json';

const browserLanguage = navigator.language?.toLowerCase().startsWith('de') ? 'de' : 'en';

let preferredLanguage = browserLanguage;
try {
  const saved = localStorage.getItem('listful:language');
  if (saved === 'en' || saved === 'de') preferredLanguage = saved;
} catch { /* Fall back to the browser language when storage is unavailable. */ }

export const i18n = createI18n({
  legacy: false,
  locale: preferredLanguage,
  fallbackLocale: 'en',
  messages: { en, de }
});
