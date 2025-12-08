import { useState, useEffect } from 'react';
import * as WebBrowser from 'expo-web-browser';
import * as Linking from 'expo-linking';
import { useAuthStore } from '@/stores/authStore';
import { getErrorMessage, logError } from '@/utils/errorHandler';
import { API_HOST } from '@/constants/api';
import { removeTokens, removeItem, STORAGE_KEYS, setAccessToken } from '@/utils/storage';
import { getMyProfile } from '@/api/auth.api';

/**
 * Google OAuth Hook (Custom Tabs 방식)
 *
 * Issue #46: 백엔드 웹 OAuth 클라이언트 ID만 사용하는 방식
 * - Custom Tabs/ASWebAuthenticationSession 사용
 * - 딥링크로 토큰 수신: upvy://oauth/callback
 * - state 파라미터로 모바일 구분: mobile:xxx
 */
export const useGoogleAuth = () => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { login } = useAuthStore();

  // 딥링크 리스너
  useEffect(() => {
    const handleDeepLink = async (event: { url: string }) => {
      try {
        const url = event.url;

        // upvy://oauth/callback?accessToken=...&refreshToken=...
        if (url.startsWith('upvy://oauth/callback')) {
          // OAuth 브라우저 자동으로 닫기
          WebBrowser.dismissBrowser();

          const parsed = Linking.parse(url);
          const { accessToken, refreshToken, userId, email, error: errorParam } = parsed.queryParams as {
            accessToken?: string;
            refreshToken?: string;
            userId?: string;
            email?: string;
            error?: string;
          };

          if (errorParam) {
            throw new Error(errorParam);
          }

          if (!accessToken || !refreshToken) {
            throw new Error('토큰을 받지 못했습니다.');
          }

          // AsyncStorage에서 기존 토큰 완전히 삭제
          await removeTokens();
          await removeItem(STORAGE_KEYS.USER_INFO);

          // 토큰을 먼저 저장 (프로필 조회 API에 필요)
          await setAccessToken(accessToken);

          // 프로필 조회 시도 (있으면 함께 저장, 없으면 ProfileSetup으로)
          let profile = null;
          try {
            profile = await getMyProfile();
          } catch (profileError) {
            // 프로필이 없는 경우 (첫 로그인) - ProfileSetup으로 이동
          }

          // Zustand Store에 저장
          await login(
            accessToken,
            refreshToken,
            {
              id: userId || '',
              email: email || '',
              provider: 'GOOGLE',
              role: 'USER',
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
            },
            profile || undefined
          );

          setIsLoading(false);
        }
      } catch (err) {
        const message = getErrorMessage(err);
        setError(message);
        logError(err, 'useGoogleAuth.handleDeepLink');
        setIsLoading(false);

        // 에러 발생 시에도 브라우저 닫기
        WebBrowser.dismissBrowser();
      }
    };

    // 딥링크 리스너 등록
    const subscription = Linking.addEventListener('url', handleDeepLink);

    // 앱이 딥링크로 시작된 경우 처리
    Linking.getInitialURL().then((url) => {
      if (url) {
        handleDeepLink({ url });
      }
    });

    return () => {
      subscription.remove();
    };
  }, [login]);

  /**
   * Google 로그인 시작
   * Custom Tabs로 백엔드 OAuth URL 열기
   */
  const handleGoogleLogin = async () => {
    try {
      setIsLoading(true);
      setError(null);

      // state 파라미터: mobile:랜덤UUID
      const state = `mobile:${Math.random().toString(36).substring(7)}`;

      // 백엔드 OAuth URL
      const authUrl = `${API_HOST}/oauth2/authorization/google?state=${state}`;

      // Custom Tabs로 OAuth 시작
      const result = await WebBrowser.openBrowserAsync(authUrl, {
        // Android에서 Custom Tabs 사용
        showTitle: true,
        toolbarColor: '#34C759',
        enableBarCollapsing: false,
      });

      // 사용자가 브라우저를 닫은 경우 (딥링크로 돌아오지 않음)
      if (result.type === 'cancel' || result.type === 'dismiss') {
        setIsLoading(false);
      }
      // 딥링크 리스너에서 처리됨 (로딩 상태 유지)
    } catch (err) {
      const message = getErrorMessage(err);
      setError(message);
      logError(err, 'useGoogleAuth.handleGoogleLogin');
      setIsLoading(false);
    }
  };

  return {
    /** 로딩 상태 */
    isLoading,
    /** 에러 메시지 */
    error,
    /** Google 로그인 핸들러 */
    handleGoogleLogin,
    /** OAuth 준비 상태 (항상 true, Client ID 불필요) */
    isReady: true,
  };
};
