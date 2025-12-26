/**
 * App Version API 클라이언트
 *
 * 백엔드: me.onetwo.upvy.domain.app.controller.AppVersionController
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type {
  AppVersionCheckRequest,
  AppVersionCheckResponse,
} from '@/types/app-version.types';

/**
 * 앱 버전 체크
 *
 * 클라이언트의 현재 버전과 플랫폼 정보를 전송하여
 * 강제 업데이트 필요 여부와 최신 버전 정보를 확인합니다.
 *
 * 백엔드: POST /api/v1/app-version/check
 * Response: 200 OK - AppVersionCheckResponse
 *
 * @param request 앱 버전 체크 요청
 * @returns 앱 버전 체크 응답
 */
export const checkAppVersion = async (
  request: AppVersionCheckRequest
): Promise<AppVersionCheckResponse> => {
  const response = await apiClient.post<AppVersionCheckResponse>(
    API_ENDPOINTS.APP_VERSION.CHECK,
    request
  );
  return response.data;
};
