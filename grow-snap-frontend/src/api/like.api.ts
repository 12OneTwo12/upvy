/**
 * 좋아요 API 클라이언트
 *
 * 백엔드 스펙: POST/DELETE 방식 (Toggle 아님)
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';

/**
 * 좋아요 추가
 *
 * 백엔드: POST /api/v1/likes/{contentId}
 * Response: 201 Created
 *
 * @param contentId 콘텐츠 ID
 */
export const createLike = async (contentId: string): Promise<void> => {
  await apiClient.post(API_ENDPOINTS.LIKE.CREATE(contentId));
};

/**
 * 좋아요 취소
 *
 * 백엔드: DELETE /api/v1/likes/{contentId}
 * Response: 204 No Content
 *
 * @param contentId 콘텐츠 ID
 */
export const deleteLike = async (contentId: string): Promise<void> => {
  await apiClient.delete(API_ENDPOINTS.LIKE.DELETE(contentId));
};

/**
 * 좋아요 상태 확인
 *
 * 백엔드: GET /api/v1/likes/{contentId}/check
 * Response: { isLiked: boolean }
 *
 * @param contentId 콘텐츠 ID
 * @returns 좋아요 상태
 */
export const checkLike = async (contentId: string): Promise<boolean> => {
  const response = await apiClient.get<{ isLiked: boolean }>(
    API_ENDPOINTS.LIKE.CHECK(contentId)
  );
  return response.data.isLiked;
};

/**
 * 좋아요 개수 조회
 *
 * 백엔드: GET /api/v1/likes/{contentId}/count
 * Response: { count: number }
 *
 * @param contentId 콘텐츠 ID
 * @returns 좋아요 개수
 */
export const getLikeCount = async (contentId: string): Promise<number> => {
  const response = await apiClient.get<{ count: number }>(
    API_ENDPOINTS.LIKE.COUNT(contentId)
  );
  return response.data.count;
};
