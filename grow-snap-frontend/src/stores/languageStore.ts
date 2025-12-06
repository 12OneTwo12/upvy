import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import i18n, { supportedLanguages } from '@/locales';
import type { SupportedLanguage } from '@/locales';

const LANGUAGE_STORAGE_KEY = '@growsnap:language';

interface LanguageState {
  currentLanguage: SupportedLanguage;
  isInitialized: boolean;
  setLanguage: (language: SupportedLanguage) => Promise<void>;
  initializeLanguage: () => Promise<void>;
}

export const useLanguageStore = create<LanguageState>((set, get) => ({
  currentLanguage: 'ko',
  isInitialized: false,

  /**
   * Set and persist language preference
   */
  setLanguage: async (language: SupportedLanguage) => {
    try {
      // Change i18n language
      await i18n.changeLanguage(language);

      // Persist to AsyncStorage
      await AsyncStorage.setItem(LANGUAGE_STORAGE_KEY, language);

      // Update state
      set({ currentLanguage: language });
    } catch (error) {
      console.error('Failed to set language:', error);
    }
  },

  /**
   * Initialize language from AsyncStorage or device settings
   */
  initializeLanguage: async () => {
    try {
      // Try to get saved language from AsyncStorage
      const savedLanguage = await AsyncStorage.getItem(LANGUAGE_STORAGE_KEY);

      if (savedLanguage && supportedLanguages.some(lang => lang.code === savedLanguage)) {
        // Use saved language
        await i18n.changeLanguage(savedLanguage);
        set({ currentLanguage: savedLanguage as SupportedLanguage, isInitialized: true });
      } else {
        // Use device language (already set in i18n initialization)
        const deviceLanguage = i18n.language as SupportedLanguage;
        set({ currentLanguage: deviceLanguage, isInitialized: true });
        // Save device language to AsyncStorage
        await AsyncStorage.setItem(LANGUAGE_STORAGE_KEY, deviceLanguage);
      }
    } catch (error) {
      console.error('Failed to initialize language:', error);
      // Fallback to default (ko)
      set({ currentLanguage: 'ko', isInitialized: true });
    }
  },
}));
