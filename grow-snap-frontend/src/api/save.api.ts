/**
 * 저장 API 클라이언트
 *
 * 백엔드 스펙: POST/DELETE 방식 (Toggle 아님)
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';

/**
 * 콘텐츠 저장
 *
 * 백엔드: POST /api/v1/saves/{contentId}
 * Response: 201 Created
 *
 * @param contentId 콘텐츠 ID
 */
export const createSave = async (contentId: string): Promise<void> => {
  await apiClient.post(API_ENDPOINTS.SAVE.CREATE(contentId));
};

/**
 * 콘텐츠 저장 취소
 *
 * 백엔드: DELETE /api/v1/saves/{contentId}
 * Response: 204 No Content
 *
 * @param contentId 콘텐츠 ID
 */
export const deleteSave = async (contentId: string): Promise<void> => {
  await apiClient.delete(API_ENDPOINTS.SAVE.DELETE(contentId));
};

/**
 * 저장 상태 확인
 *
 * 백엔드: GET /api/v1/saves/{contentId}/check
 * Response: { isSaved: boolean }
 *
 * @param contentId 콘텐츠 ID
 * @returns 저장 상태
 */
export const checkSave = async (contentId: string): Promise<boolean> => {
  const response = await apiClient.get<{ isSaved: boolean }>(
    API_ENDPOINTS.SAVE.CHECK(contentId)
  );
  return response.data.isSaved;
};

/**
 * 저장한 콘텐츠 목록 조회
 *
 * 백엔드: GET /api/v1/saves/me
 * Response: SavedContent[]
 *
 * @returns 저장한 콘텐츠 목록
 */
export const getSavedContentList = async (): Promise<any[]> => {
  const response = await apiClient.get(API_ENDPOINTS.SAVE.LIST);
  return response.data;
};
