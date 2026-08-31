import { createI18n } from 'vue-i18n';
import en from './locales/en.json';
import de from './locales/de.json';

const browserLanguage = navigator.language?.toLowerCase().startsWith('de') ? 'de' : 'en';

export const i18n = createI18n({
  legacy: false,
  locale: browserLanguage,
  fallbackLocale: 'en',
  messages: { en, de }
});
