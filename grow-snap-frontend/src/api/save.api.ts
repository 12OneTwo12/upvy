/**
 * 저장 API 클라이언트
 *
 * 백엔드: me.onetwo.growsnap.domain.interaction.controller.SaveController
 * 백엔드 스펙: POST/DELETE 방식 (Toggle 아님)
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type { SaveResponse, SaveStatusResponse, SavedContentResponse } from '@/types/interaction.types';

/**
 * 콘텐츠 저장
 *
 * 백엔드: POST /api/v1/contents/{contentId}/save
 * Response: SaveResponse { contentId, saveCount, isSaved }
 *
 * @param contentId 콘텐츠 ID
 * @returns 저장 응답
 */
export const createSave = async (contentId: string): Promise<SaveResponse> => {
  const response = await apiClient.post<SaveResponse>(
    API_ENDPOINTS.SAVE.CREATE(contentId)
  );
  return response.data;
};

/**
 * 콘텐츠 저장 취소
 *
 * 백엔드: DELETE /api/v1/contents/{contentId}/save
 * Response: SaveResponse { contentId, saveCount, isSaved }
 *
 * @param contentId 콘텐츠 ID
 * @returns 저장 응답
 */
export const deleteSave = async (contentId: string): Promise<SaveResponse> => {
  const response = await apiClient.delete<SaveResponse>(
    API_ENDPOINTS.SAVE.DELETE(contentId)
  );
  return response.data;
};

/**
 * 저장 상태 조회
 *
 * 백엔드: GET /api/v1/contents/{contentId}/save/status
 * Response: SaveStatusResponse { contentId, isSaved }
 *
 * @param contentId 콘텐츠 ID
 * @returns 저장 상태
 */
export const getSaveStatus = async (contentId: string): Promise<SaveStatusResponse> => {
  const response = await apiClient.get<SaveStatusResponse>(
    API_ENDPOINTS.SAVE.STATUS(contentId)
  );
  return response.data;
};

/**
 * 저장한 콘텐츠 목록 조회
 *
 * 백엔드: GET /api/v1/users/me/saved-contents
 * Response: SavedContentResponse[]
 *
 * @returns 저장한 콘텐츠 목록
 */
export const getSavedContentList = async (): Promise<SavedContentResponse[]> => {
  const response = await apiClient.get<SavedContentResponse[]>(
    API_ENDPOINTS.SAVE.LIST
  );
  return response.data;
};
