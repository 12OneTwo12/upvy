/**
 * 저장 API 클라이언트
 *
 * 백엔드: me.onetwo.upvy.domain.interaction.controller.SaveController
 * 백엔드 스펙: POST/DELETE 방식 (Toggle 아님)
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type { SaveResponse, SaveStatusResponse } from '@/types/interaction.types';
import type { ContentResponse } from '@/types/content.types';
import type { CursorPageResponse, CursorPageParams } from '@/types/pagination.types';

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
 * 저장한 콘텐츠 목록을 커서 기반 페이징으로 조회
 *
 * 백엔드: GET /api/v1/users/me/saved-contents?cursor={cursor}&limit={limit}
 * Response: CursorPageResponse<ContentResponse>
 *
 * 백엔드 업데이트: 이제 ContentResponse를 반환하여 다른 콘텐츠 조회 API와 일관성 유지
 *
 * @param params 커서 페이지 파라미터 (cursor, limit)
 * @returns 커서 페이지 응답 (ContentResponse 형식)
 */
export const getSavedContentList = async (
  params?: CursorPageParams
): Promise<CursorPageResponse<ContentResponse>> => {
  const response = await apiClient.get<CursorPageResponse<ContentResponse>>(
    API_ENDPOINTS.SAVE.LIST,
    { params }
  );
  return response.data;
};
