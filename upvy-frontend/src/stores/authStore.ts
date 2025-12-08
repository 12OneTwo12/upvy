import { create } from 'zustand';
import { User, UserProfile } from '@/types/auth.types';
import {
  setAccessToken,
  setRefreshToken,
  removeTokens,
  getAccessToken,
  getRefreshToken,
  setItem,
  getItem,
  removeItem,
  STORAGE_KEYS,
} from '@/utils/storage';
import { getCurrentUser, getMyProfile, logout as apiLogout } from '@/api/auth.api';

interface AuthState {
  // State
  user: User | null;
  profile: UserProfile | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  // Actions
  setUser: (user: User | null) => void;
  setProfile: (profile: UserProfile | null) => void;
  login: (accessToken: string, refreshToken: string, user: User, profile?: UserProfile) => Promise<void>;
  logout: () => Promise<void>;
  checkAuth: () => Promise<void>;
  updateProfile: (profile: UserProfile) => void;
}

/**
 * Auth Store
 * 사용자 인증 상태를 관리합니다.
 */
export const useAuthStore = create<AuthState>((set, get) => ({
  // Initial State
  user: null,
  profile: null,
  isAuthenticated: false,
  isLoading: true, // 앱 시작 시 자동 로그인 체크

  // Set User
  setUser: (user) => {
    set({ user, isAuthenticated: !!user });
  },

  // Set Profile
  setProfile: (profile) => {
    set({ profile });
  },

  // Login
  login: async (accessToken, refreshToken, user, profile) => {
    try {
      // Save tokens
      await setAccessToken(accessToken);
      await setRefreshToken(refreshToken);

      // Save user info
      await setItem(STORAGE_KEYS.USER_INFO, user);

      // Update state
      set({
        user,
        profile: profile || null,
        isAuthenticated: true,
        isLoading: false,
      });
    } catch (error) {
      console.error('Login error:', error);
      throw error;
    }
  },

  // Logout
  logout: async () => {
    try {
      // 백엔드 로그아웃 API 호출 (리프레시 토큰 무효화)
      try {
        await apiLogout();
      } catch (error) {
        // 백엔드 로그아웃 실패해도 클라이언트 정리는 계속 진행
        console.warn('Backend logout failed:', error);
      }

      // Remove tokens
      await removeTokens();
      await removeItem(STORAGE_KEYS.USER_INFO);

      // Clear state
      set({
        user: null,
        profile: null,
        isAuthenticated: false,
        isLoading: false,
      });
    } catch (error) {
      console.error('Logout error:', error);
    }
  },

  // Check Auth (앱 시작 시 자동 로그인)
  checkAuth: async () => {
    try {
      set({ isLoading: true });

      const accessToken = await getAccessToken();
      const refreshToken = await getRefreshToken();
      const savedUser = await getItem<User>(STORAGE_KEYS.USER_INFO);

      if (!accessToken || !refreshToken || !savedUser) {
        set({ isLoading: false, isAuthenticated: false });
        return;
      }

      // Verify token by fetching current user
      try {
        const user = await getCurrentUser();

        // 프로필 조회 (프로필이 없는 경우 404 에러 발생)
        let profile: UserProfile | null = null;
        try {
          profile = await getMyProfile();
        } catch (profileError) {
          // 프로필이 없는 경우 (첫 로그인)
          console.log('No profile found, user needs to create profile');
        }

        set({
          user,
          profile,
          isAuthenticated: true,
          isLoading: false,
        });
      } catch (error) {
        // Token expired or invalid
        await get().logout();
      }
    } catch (error) {
      console.error('Check auth error:', error);
      set({ isLoading: false, isAuthenticated: false });
    }
  },

  // Update Profile
  updateProfile: (profile) => {
    set({ profile });
  },
}));
