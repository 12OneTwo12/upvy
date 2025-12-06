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
import enNotification from './en/notification.json';

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
import koNotification from './ko/notification.json';

import jaCommon from './ja/common.json';
import jaErrors from './ja/errors.json';
import jaAuth from './ja/auth.json';
import jaSettings from './ja/settings.json';
import jaProfile from './ja/profile.json';
import jaFeed from './ja/feed.json';
import jaUpload from './ja/upload.json';
import jaSearch from './ja/search.json';
import jaInteractions from './ja/interactions.json';
import jaLegal from './ja/legal.json';
import jaNotification from './ja/notification.json';

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
    notification: enNotification,
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
    notification: koNotification,
  },
  ja: {
    common: jaCommon,
    errors: jaErrors,
    auth: jaAuth,
    settings: jaSettings,
    profile: jaProfile,
    feed: jaFeed,
    upload: jaUpload,
    search: jaSearch,
    interactions: jaInteractions,
    legal: jaLegal,
    notification: jaNotification,
  },
};

// Get device language (fallback to 'ko')
const getDeviceLanguage = (): string => {
  const locales = Localization.getLocales();
  if (locales && locales.length > 0) {
    const languageCode = locales[0].languageCode;
    // Support 'en', 'ko', and 'ja'
    if (languageCode === 'en' || languageCode === 'ko' || languageCode === 'ja') {
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
    ns: ['common', 'errors', 'auth', 'settings', 'profile', 'feed', 'upload', 'search', 'interactions', 'legal', 'notification'],
    interpolation: {
      escapeValue: false, // React already escapes values
    },
    compatibilityJSON: 'v3', // For pluralization
  });

export default i18n;

export const supportedLanguages = [
  { code: 'ko', name: '한국어', nameEn: 'Korean' },
  { code: 'en', name: 'English', nameEn: 'English' },
  { code: 'ja', name: '日本語', nameEn: 'Japanese' },
] as const;

export type SupportedLanguage = typeof supportedLanguages[number]['code'];
