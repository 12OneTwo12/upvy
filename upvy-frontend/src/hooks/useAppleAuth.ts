import { useState, useEffect } from 'react';
import * as AppleAuthentication from 'expo-apple-authentication';
import { Platform } from 'react-native';
import { useAuthStore } from '@/stores/authStore';
import { getErrorMessage, logError } from '@/utils/errorHandler';
import { API_HOST } from '@/constants/api';
import { removeTokens, removeItem, STORAGE_KEYS, setAccessToken } from '@/utils/storage';
import { getMyProfile } from '@/api/auth.api';

/**
 * Apple OAuth Hook (Sign in with Apple)
 *
 * iOS App Store 심사 가이드라인 4.8 준수
 * - expo-apple-authentication 네이티브 API 사용
 * - identityToken을 백엔드 REST API로 전송
 * - 백엔드에서 토큰 검증 후 JWT 발급
 */
export const useAppleAuth = () => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isAvailable, setIsAvailable] = useState(false);
  const { login } = useAuthStore();

  // Apple Sign In 사용 가능 여부 확인 (iOS 13+)
  useEffect(() => {
    const checkAvailability = async () => {
      if (Platform.OS === 'ios') {
        const available = await AppleAuthentication.isAvailableAsync();
        setIsAvailable(available);
      }
    };
    checkAvailability();
  }, []);

  /**
   * Apple 로그인 시작
   * Sign in with Apple 네이티브 다이얼로그 → 백엔드 API 호출
   */
  const handleAppleLogin = async () => {
    try {
      setIsLoading(true);
      setError(null);

      // 1. Apple Sign In 네이티브 다이얼로그
      const credential = await AppleAuthentication.signInAsync({
        requestedScopes: [
          AppleAuthentication.AppleAuthenticationScope.FULL_NAME,
          AppleAuthentication.AppleAuthenticationScope.EMAIL,
        ],
      });

      // 2. identityToken을 백엔드 API로 전송
      const response = await fetch(`${API_HOST}/api/v1/auth/apple/token`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          identityToken: credential.identityToken,
          authorizationCode: credential.authorizationCode,
          user: credential.fullName ? {
            familyName: credential.fullName.familyName,
            givenName: credential.fullName.givenName,
          } : null,
        }),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({ message: '로그인에 실패했습니다.' }));
        throw new Error(errorData.message || '로그인에 실패했습니다.');
      }

      const data = await response.json();
      const { accessToken, refreshToken, userId, email } = data;

      if (!accessToken || !refreshToken) {
        throw new Error('토큰을 받지 못했습니다.');
      }

      // 3. AsyncStorage에서 기존 토큰 완전히 삭제
      await removeTokens();
      await removeItem(STORAGE_KEYS.USER_INFO);

      // 4. 토큰을 먼저 저장 (프로필 조회 API에 필요)
      await setAccessToken(accessToken);

      // 5. 프로필 조회 시도 (있으면 함께 저장, 없으면 ProfileSetup으로)
      let profile = null;
      try {
        profile = await getMyProfile();
      } catch (profileError) {
        // 프로필이 없는 경우 (첫 로그인) - ProfileSetup으로 이동
      }

      // 6. Zustand Store에 저장
      await login(
        accessToken,
        refreshToken,
        {
          id: userId || '',
          email: email || '',
          provider: 'APPLE',
          role: 'USER',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        profile || undefined
      );

      setIsLoading(false);
    } catch (err: any) {
      // 사용자가 취소한 경우 (ERR_REQUEST_CANCELED)
      if (err.code === 'ERR_REQUEST_CANCELED') {
        setIsLoading(false);
        return;
      }

      const message = getErrorMessage(err);
      setError(message);
      logError(err, 'useAppleAuth.handleAppleLogin');
      setIsLoading(false);
    }
  };

  return {
    /** 로딩 상태 */
    isLoading,
    /** 에러 메시지 */
    error,
    /** Apple 로그인 핸들러 */
    handleAppleLogin,
    /** Apple Sign In 사용 가능 여부 (iOS 13+ only) */
    isAvailable,
  };
};
