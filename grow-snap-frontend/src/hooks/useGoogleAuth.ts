import { useState } from 'react';
import * as Google from 'expo-auth-session/providers/google';
import * as WebBrowser from 'expo-web-browser';
import { googleLogin } from '@/api/auth.api';
import { useAuthStore } from '@/stores/authStore';

// WebBrowser 완료 처리
WebBrowser.maybeCompleteAuthSession();

/**
 * Google OAuth Hook
 */
export const useGoogleAuth = () => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { login } = useAuthStore();

  // Google OAuth Request
  // TODO: Google Cloud Console에서 Client ID 발급 후 설정 필요
  const [request, response, promptAsync] = Google.useAuthRequest({
    expoClientId: 'YOUR_EXPO_CLIENT_ID',
    iosClientId: 'YOUR_IOS_CLIENT_ID',
    androidClientId: 'YOUR_ANDROID_CLIENT_ID',
    webClientId: 'YOUR_WEB_CLIENT_ID',
  });

  /**
   * Google 로그인 처리
   * @param onNeedProfile 프로필이 없을 때 호출되는 콜백
   */
  const handleGoogleLogin = async (onNeedProfile?: () => void) => {
    try {
      setIsLoading(true);
      setError(null);

      // Google OAuth Prompt
      const result = await promptAsync();

      if (result.type === 'success') {
        const { authentication } = result;
        if (!authentication?.accessToken) {
          throw new Error('No access token received');
        }

        // 백엔드 API 호출
        const loginResponse = await googleLogin(authentication.accessToken);

        // Store에 저장
        await login(
          loginResponse.accessToken,
          loginResponse.refreshToken,
          loginResponse.user,
          loginResponse.profile
        );

        // 프로필이 없으면 프로필 설정 화면으로 이동
        if (!loginResponse.profile && onNeedProfile) {
          onNeedProfile();
        }
      } else if (result.type === 'error') {
        throw new Error('Google login failed');
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Login failed';
      setError(message);
      console.error('Google login error:', err);
    } finally {
      setIsLoading(false);
    }
  };

  return {
    isLoading,
    error,
    handleGoogleLogin,
    isReady: !!request,
  };
};
