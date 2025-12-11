/**
 * Analytics API 클라이언트
 *
 * 백엔드: me.onetwo.upvy.domain.analytics.controller.AnalyticsController
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type { ViewEventRequest } from '@/types/analytics.types';

/**
 * 시청 이벤트 추적
 *
 * 사용자의 콘텐츠 시청 기록을 백엔드에 전송합니다.
 * - user_view_history 테이블에 기록
 * - skipped=false인 경우 view_count 증가
 *
 * 백엔드: POST /api/v1/analytics/views
 * Response: 204 No Content
 *
 * @param request 시청 이벤트 요청
 */
export const trackView = async (request: ViewEventRequest): Promise<void> => {
  await apiClient.post<void>(API_ENDPOINTS.ANALYTICS.TRACK_VIEW, request);
};
