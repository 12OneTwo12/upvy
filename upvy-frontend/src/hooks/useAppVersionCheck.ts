/**
 * 앱 버전 체크 Hook
 *
 * 앱 시작 시 또는 특정 시점에 백엔드 API를 호출하여
 * 강제 업데이트 필요 여부를 확인합니다.
 */

import { useState, useEffect } from 'react';
import { Platform } from 'react-native';
import Constants from 'expo-constants';
import { checkAppVersion } from '@/api/app-version.api';
import type { AppVersionCheckResponse } from '@/types/app-version.types';

/**
 * 앱 버전 체크 Hook
 *
 * @param enabled 버전 체크 활성화 여부 (기본값: true)
 * @returns 버전 체크 결과 및 상태
 */
export const useAppVersionCheck = (enabled = true) => {
  const [versionInfo, setVersionInfo] = useState<AppVersionCheckResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!enabled) return;

    const checkVersion = async () => {
      try {
        setIsLoading(true);
        setError(null);

        // 현재 앱 버전 가져오기
        const currentVersion = Constants.expoConfig?.version || '1.0.0';

        // 플랫폼 결정
        const platform = Platform.OS === 'ios' ? 'IOS' : 'ANDROID';

        // 백엔드 API 호출
        const response = await checkAppVersion({
          platform,
          currentVersion,
        });

        setVersionInfo(response);
      } catch (err) {
        console.error('[useAppVersionCheck] Version check failed:', err);
        setError(err instanceof Error ? err : new Error('Unknown error'));
      } finally {
        setIsLoading(false);
      }
    };

    checkVersion();
  }, [enabled]);

  return {
    versionInfo,
    isLoading,
    error,
    needsUpdate: versionInfo?.needsUpdate || false,
    isLatestVersion: versionInfo?.isLatestVersion || false,
  };
};
