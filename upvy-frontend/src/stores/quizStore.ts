/**
 * Quiz Store
 *
 * 퀴즈 관련 전역 상태를 관리합니다.
 * - 퀴즈 자동 표시 ON/OFF 토글 상태
 */

import { create } from 'zustand';
import { setItem, getItem, STORAGE_KEYS } from '@/utils/storage';

interface QuizState {
  // State
  isQuizAutoDisplayEnabled: boolean; // 퀴즈 자동 표시 여부

  // Actions
  setQuizAutoDisplay: (enabled: boolean) => Promise<void>;
  toggleQuizAutoDisplay: () => Promise<void>;
  initializeQuizSettings: () => Promise<void>;
}

/**
 * Quiz Store
 *
 * 퀴즈 자동 표시 설정을 관리합니다.
 */
export const useQuizStore = create<QuizState>((set, get) => ({
  // Initial State
  isQuizAutoDisplayEnabled: true, // 기본값: 자동 표시 활성화

  // Set Quiz Auto Display
  setQuizAutoDisplay: async (enabled) => {
    try {
      // Save to AsyncStorage
      await setItem(STORAGE_KEYS.QUIZ_AUTO_DISPLAY, enabled);

      // Update state
      set({ isQuizAutoDisplayEnabled: enabled });
    } catch (error) {
      console.error('Failed to save quiz auto display setting:', error);
    }
  },

  // Toggle Quiz Auto Display
  toggleQuizAutoDisplay: async () => {
    const currentState = get().isQuizAutoDisplayEnabled;
    await get().setQuizAutoDisplay(!currentState);
  },

  // Initialize Quiz Settings (앱 시작 시 호출)
  initializeQuizSettings: async () => {
    try {
      // Load saved setting from AsyncStorage
      const savedSetting = await getItem<boolean>(STORAGE_KEYS.QUIZ_AUTO_DISPLAY);

      // Use saved setting or default to true
      const isEnabled = savedSetting !== null ? savedSetting : true;

      // Update state
      set({ isQuizAutoDisplayEnabled: isEnabled });
    } catch (error) {
      console.error('Failed to initialize quiz settings:', error);
      // Fallback to default (enabled)
      set({ isQuizAutoDisplayEnabled: true });
    }
  },
}));
