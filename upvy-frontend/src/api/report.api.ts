/**
 * 신고 API 클라이언트
 * 백엔드 Controller: upvy-backend/.../report/controller/ReportController.kt
 */

import apiClient from './client';
import type { ReportRequest, ReportResponse, TargetType } from '@/types/report.types';

/**
 * 대상 신고
 * POST /api/v1/reports/{targetType}/{targetId}
 *
 * @param targetType 신고 대상 타입 (content, user)
 * @param targetId 신고 대상 ID
 * @param data 신고 요청 데이터
 * @returns 신고 응답
 */
export const reportTarget = async (
  targetType: TargetType,
  targetId: string,
  data: ReportRequest
): Promise<ReportResponse> => {
  // IMPORTANT: Enum PathVariable은 소문자로 전송해야 함 (백엔드 Converter 규칙)
  const response = await apiClient.post<ReportResponse>(
    `/reports/${targetType.toLowerCase()}/${targetId}`,
    data
  );
  return response.data;
};
