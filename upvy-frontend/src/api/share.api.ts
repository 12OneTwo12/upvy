/**
 * 공유 API 클라이언트
 *
 * 백엔드: me.onetwo.upvy.domain.interaction.controller.ShareController
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type { ShareResponse, ShareLinkResponse } from '@/types/interaction.types';

/**
 * 콘텐츠 공유 (카운트 증가)
 *
 * 백엔드: POST /api/v1/contents/{contentId}/share
 * Response: ShareResponse { contentId, shareCount }
 *
 * @param contentId 콘텐츠 ID
 * @returns 공유 응답
 */
export const shareContent = async (contentId: string): Promise<ShareResponse> => {
  const response = await apiClient.post<ShareResponse>(
    API_ENDPOINTS.SHARE.CREATE(contentId)
  );
  return response.data;
};

/**
 * 공유 링크 조회
 *
 * 백엔드: GET /api/v1/contents/{contentId}/share-link
 * Response: ShareLinkResponse { contentId, shareUrl }
 *
 * @param contentId 콘텐츠 ID
 * @returns 공유 링크 응답
 */
export const getShareLink = async (contentId: string): Promise<ShareLinkResponse> => {
  const response = await apiClient.get<ShareLinkResponse>(
    API_ENDPOINTS.SHARE.GET_LINK(contentId)
  );
  return response.data;
};
