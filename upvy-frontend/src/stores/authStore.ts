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
import { getCurrentUser, getMyProfile, getTermsAgreement, logout as apiLogout } from '@/api/auth.api';
import { setSentryUser, clearSentryUser } from '@/config/sentry';
import { queryClient } from '../../App';

interface AuthState {
  // State
  user: User | null;
  profile: UserProfile | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  hasAgreedToTerms: boolean;

  // Actions
  setUser: (user: User | null) => void;
  setProfile: (profile: UserProfile | null) => void;
  setHasAgreedToTerms: (hasAgreed: boolean) => void;
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
  hasAgreedToTerms: false,

  // Set User
  setUser: (user) => {
    set({ user, isAuthenticated: !!user });
  },

  // Set Profile
  setProfile: (profile) => {
    set({ profile });
  },

  // Set Has Agreed To Terms
  setHasAgreedToTerms: (hasAgreed) => {
    set({ hasAgreedToTerms: hasAgreed });
  },

  // Login
  login: async (accessToken, refreshToken, user, profile) => {
    try {
      // Save tokens
      await setAccessToken(accessToken);
      await setRefreshToken(refreshToken);

      // Save user info
      await setItem(STORAGE_KEYS.USER_INFO, user);

      // Set Sentry user context
      setSentryUser({
        id: user.id,
        email: user.email,
        username: profile?.nickname || user.email,
      });

      // Update state
      set({
        user,
        profile: profile || null,
        isAuthenticated: true,
        isLoading: false,
      });
    } catch (error) {
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

      // Clear Sentry user context
      clearSentryUser();

      // Clear React Query cache
      queryClient.clear();

      // Clear state
      set({
        user: null,
        profile: null,
        isAuthenticated: false,
        isLoading: false,
        hasAgreedToTerms: false,
      });
    } catch (error) {
      // Logout error silently ignored
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

        // 약관 동의 여부 조회
        let hasAgreedToTerms = false;
        try {
          const termsAgreement = await getTermsAgreement();
          hasAgreedToTerms = termsAgreement.isAllRequiredAgreed;
        } catch (termsError) {
          // 약관 동의 정보가 없는 경우 (첫 로그인)
        }

        // 프로필 조회 (프로필이 없는 경우 404 에러 발생)
        let profile: UserProfile | null = null;
        try {
          profile = await getMyProfile();
        } catch (profileError) {
          // 프로필이 없는 경우 (첫 로그인)
        }

        // Set Sentry user context (auto-login)
        setSentryUser({
          id: user.id,
          email: user.email,
          username: profile?.nickname || user.email,
        });

        set({
          user,
          profile,
          isAuthenticated: true,
          isLoading: false,
          hasAgreedToTerms,
        });
      } catch (error) {
        // Token expired or invalid
        await get().logout();
      }
    } catch (error) {
      set({ isLoading: false, isAuthenticated: false });
    }
  },

  // Update Profile
  updateProfile: (profile) => {
    set({ profile });
  },
}));
