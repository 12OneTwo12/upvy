import { create } from 'zustand';
import { Appearance, ColorSchemeName } from 'react-native';
import { setItem, getItem, STORAGE_KEYS } from '@/utils/storage';

/**
 * Theme Mode
 * - light: 라이트 모드 고정
 * - dark: 다크 모드 고정
 * - system: 시스템 설정 따르기
 */
export type ThemeMode = 'light' | 'dark' | 'system';

interface ThemeState {
  // State
  theme: ThemeMode;
  isDarkMode: boolean;

  // Actions
  setTheme: (theme: ThemeMode) => Promise<void>;
  initializeTheme: () => Promise<void>;
}

/**
 * Get actual dark mode state based on theme mode and system appearance
 */
const getActualDarkMode = (themeMode: ThemeMode, systemColorScheme: ColorSchemeName): boolean => {
  if (themeMode === 'system') {
    return systemColorScheme === 'dark';
  }
  return themeMode === 'dark';
};

/**
 * Theme Store
 * 테마 상태를 관리합니다.
 */
export const useThemeStore = create<ThemeState>((set, get) => ({
  // Initial State
  theme: 'system', // 기본값: 시스템 설정 따르기
  isDarkMode: Appearance.getColorScheme() === 'dark', // 초기 시스템 테마

  // Set Theme
  setTheme: async (theme) => {
    try {
      // Save to AsyncStorage
      await setItem(STORAGE_KEYS.THEME_MODE, theme);

      // Get system color scheme
      const systemColorScheme = Appearance.getColorScheme();

      // Calculate actual dark mode state
      const isDarkMode = getActualDarkMode(theme, systemColorScheme);

      // Update state
      set({ theme, isDarkMode });
    } catch (error) {
      console.error('Failed to save theme:', error);
    }
  },

  // Initialize Theme (앱 시작 시 호출)
  initializeTheme: async () => {
    try {
      // Load saved theme from AsyncStorage
      const savedTheme = await getItem<ThemeMode>(STORAGE_KEYS.THEME_MODE);

      // Get system color scheme
      const systemColorScheme = Appearance.getColorScheme();

      // Use saved theme or default to 'system'
      const theme = savedTheme || 'system';

      // Calculate actual dark mode state
      const isDarkMode = getActualDarkMode(theme, systemColorScheme);

      // Update state
      set({ theme, isDarkMode });

      // Listen to system appearance changes
      const subscription = Appearance.addChangeListener(({ colorScheme }) => {
        const currentTheme = get().theme;
        if (currentTheme === 'system') {
          set({ isDarkMode: colorScheme === 'dark' });
        }
      });

      // Return cleanup function (Note: Zustand doesn't support cleanup, but we keep the listener active)
      // In a real app, you might want to clean this up in App.tsx unmount
    } catch (error) {
      console.error('Failed to initialize theme:', error);
      // Fallback to system theme
      const systemColorScheme = Appearance.getColorScheme();
      set({ theme: 'system', isDarkMode: systemColorScheme === 'dark' });
    }
  },
}));
