/**
 * 좋아요 API 클라이언트
 *
 * 백엔드: me.onetwo.growsnap.domain.interaction.controller.LikeController
 * 백엔드 스펙: POST/DELETE 방식 (Toggle 아님)
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type { LikeResponse, LikeCountResponse, LikeStatusResponse } from '@/types/interaction.types';

/**
 * 좋아요 추가
 *
 * 백엔드: POST /api/v1/contents/{contentId}/like
 * Response: LikeResponse { contentId, likeCount, isLiked }
 *
 * @param contentId 콘텐츠 ID
 * @returns 좋아요 응답
 */
export const createLike = async (contentId: string): Promise<LikeResponse> => {
  const response = await apiClient.post<LikeResponse>(
    API_ENDPOINTS.LIKE.CREATE(contentId)
  );
  return response.data;
};

/**
 * 좋아요 취소
 *
 * 백엔드: DELETE /api/v1/contents/{contentId}/like
 * Response: LikeResponse { contentId, likeCount, isLiked }
 *
 * @param contentId 콘텐츠 ID
 * @returns 좋아요 응답
 */
export const deleteLike = async (contentId: string): Promise<LikeResponse> => {
  const response = await apiClient.delete<LikeResponse>(
    API_ENDPOINTS.LIKE.DELETE(contentId)
  );
  return response.data;
};

/**
 * 좋아요 상태 조회
 *
 * 백엔드: GET /api/v1/contents/{contentId}/like/status
 * Response: LikeStatusResponse { contentId, isLiked }
 *
 * @param contentId 콘텐츠 ID
 * @returns 좋아요 상태
 */
export const getLikeStatus = async (contentId: string): Promise<LikeStatusResponse> => {
  const response = await apiClient.get<LikeStatusResponse>(
    API_ENDPOINTS.LIKE.STATUS(contentId)
  );
  return response.data;
};

/**
 * 좋아요 개수 조회
 *
 * 백엔드: GET /api/v1/contents/{contentId}/likes
 * Response: LikeCountResponse { contentId, likeCount }
 *
 * @param contentId 콘텐츠 ID
 * @returns 좋아요 개수 응답
 */
export const getLikeCount = async (contentId: string): Promise<LikeCountResponse> => {
  const response = await apiClient.get<LikeCountResponse>(
    API_ENDPOINTS.LIKE.COUNT(contentId)
  );
  return response.data;
};
