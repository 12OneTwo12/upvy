import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import * as Localization from 'expo-localization';

// Import translations
import enCommon from './en/common.json';
import enErrors from './en/errors.json';
import enAuth from './en/auth.json';
import enSettings from './en/settings.json';
import enProfile from './en/profile.json';
import enFeed from './en/feed.json';
import enUpload from './en/upload.json';
import enSearch from './en/search.json';
import enInteractions from './en/interactions.json';
import enLegal from './en/legal.json';

import koCommon from './ko/common.json';
import koErrors from './ko/errors.json';
import koAuth from './ko/auth.json';
import koSettings from './ko/settings.json';
import koProfile from './ko/profile.json';
import koFeed from './ko/feed.json';
import koUpload from './ko/upload.json';
import koSearch from './ko/search.json';
import koInteractions from './ko/interactions.json';
import koLegal from './ko/legal.json';

// Language resources
const resources = {
  en: {
    common: enCommon,
    errors: enErrors,
    auth: enAuth,
    settings: enSettings,
    profile: enProfile,
    feed: enFeed,
    upload: enUpload,
    search: enSearch,
    interactions: enInteractions,
    legal: enLegal,
  },
  ko: {
    common: koCommon,
    errors: koErrors,
    auth: koAuth,
    settings: koSettings,
    profile: koProfile,
    feed: koFeed,
    upload: koUpload,
    search: koSearch,
    interactions: koInteractions,
    legal: koLegal,
  },
};

// Get device language (fallback to 'ko')
const getDeviceLanguage = (): string => {
  const locales = Localization.getLocales();
  if (locales && locales.length > 0) {
    const languageCode = locales[0].languageCode;
    // Support only 'en' and 'ko'
    if (languageCode === 'en' || languageCode === 'ko') {
      return languageCode;
    }
  }
  return 'ko'; // Default to Korean
};

// Initialize i18n
i18n
  .use(initReactI18next)
  .init({
    resources,
    lng: getDeviceLanguage(),
    fallbackLng: 'ko',
    defaultNS: 'common',
    ns: ['common', 'errors', 'auth', 'settings', 'profile', 'feed', 'upload', 'search', 'interactions', 'legal'],
    interpolation: {
      escapeValue: false, // React already escapes values
    },
    compatibilityJSON: 'v3', // For pluralization
  });

export default i18n;

export const supportedLanguages = [
  { code: 'ko', name: '한국어', nameEn: 'Korean' },
  { code: 'en', name: 'English', nameEn: 'English' },
] as const;

export type SupportedLanguage = typeof supportedLanguages[number]['code'];
