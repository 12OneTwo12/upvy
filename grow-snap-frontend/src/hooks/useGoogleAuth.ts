import { useState } from 'react';
import * as Google from 'expo-auth-session/providers/google';
import * as WebBrowser from 'expo-web-browser';
import { googleLogin } from '@/api/auth.api';
import { useAuthStore } from '@/stores/authStore';
import { getErrorMessage, logError } from '@/utils/errorHandler';

// WebBrowser 완료 처리
WebBrowser.maybeCompleteAuthSession();

/**
 * Google OAuth Hook
 * Google 로그인 플로우를 관리합니다.
 */
export const useGoogleAuth = () => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { login } = useAuthStore();

  // Google OAuth Request
  // TODO: Google Cloud Console에서 Client ID 발급 후 설정 필요
  // 개발 중에는 에러가 발생하지만, 실제 Client ID 설정 후 정상 작동합니다.
  const [request, response, promptAsync] = Google.useAuthRequest({
    clientId: 'YOUR_CLIENT_ID', // Google Cloud Console에서 발급
    iosClientId: 'YOUR_IOS_CLIENT_ID',
    androidClientId: 'YOUR_ANDROID_CLIENT_ID',
    webClientId: 'YOUR_WEB_CLIENT_ID',
  });

  /**
   * Google 로그인 처리
   * 네비게이션은 RootNavigator에서 상태 기반으로 자동 처리됩니다.
   */
  const handleGoogleLogin = async () => {
    try {
      setIsLoading(true);
      setError(null);

      // Google OAuth Prompt
      const result = await promptAsync();

      if (result.type === 'success') {
        const { authentication } = result;

        if (!authentication?.accessToken) {
          throw new Error('Google에서 인증 토큰을 받지 못했습니다.');
        }

        // 백엔드 API 호출
        const loginResponse = await googleLogin(authentication.accessToken);

        // Zustand Store에 저장 (RootNavigator가 상태를 보고 자동으로 화면 전환)
        await login(
          loginResponse.accessToken,
          loginResponse.refreshToken,
          loginResponse.user,
          loginResponse.profile
        );
      } else if (result.type === 'error') {
        throw new Error('Google 로그인에 실패했습니다. 다시 시도해주세요.');
      } else if (result.type === 'cancel') {
        // 사용자가 취소한 경우
        setError(null);
      }
    } catch (err) {
      const message = getErrorMessage(err);
      setError(message);
      logError(err, 'useGoogleAuth.handleGoogleLogin');
    } finally {
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
    /** Google OAuth 준비 상태 (Client ID 설정 여부) */
    isReady: !!request,
  };
};
