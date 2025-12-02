/**
 * 피드 API 클라이언트
 *
 * 백엔드 API 스펙:
 * - grow-snap-backend/src/main/kotlin/me/onetwo/growsnap/domain/feed/controller/FeedController.kt
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type { FeedResponse, FeedRequest, FeedItem } from '@/types/feed.types';
import type { Category } from '@/types/content.types';

/**
 * 메인 피드 조회 (추천 알고리즘)
 *
 * 백엔드: GET /api/v1/feed
 * Query Params: cursor, limit (default 20)
 *
 * @param params 피드 요청 파라미터
 * @returns 피드 응답 (커서 기반 페이지네이션)
 */
export const getMainFeed = async (params?: FeedRequest): Promise<FeedResponse> => {
  const response = await apiClient.get<FeedResponse>(API_ENDPOINTS.FEED.MAIN, {
    params: {
      cursor: params?.cursor || undefined,
      limit: params?.limit || 20,
    },
  });
  return response.data;
};

/**
 * 팔로잉 피드 조회
 *
 * 백엔드: GET /api/v1/feed/following
 * Query Params: cursor, limit (default 20)
 *
 * @param params 피드 요청 파라미터
 * @returns 피드 응답 (커서 기반 페이지네이션)
 */
export const getFollowingFeed = async (params?: FeedRequest): Promise<FeedResponse> => {
  const response = await apiClient.get<FeedResponse>(API_ENDPOINTS.FEED.FOLLOWING, {
    params: {
      cursor: params?.cursor || undefined,
      limit: params?.limit || 20,
    },
  });
  return response.data;
};

/**
 * 피드 새로고침 (캐시 클리어)
 *
 * 백엔드: POST /api/v1/feed/refresh
 * Response: 204 No Content
 */
export const refreshFeed = async (): Promise<void> => {
  await apiClient.post(API_ENDPOINTS.FEED.REFRESH);
};

/**
 * 카테고리별 피드 조회
 *
 * 백엔드: GET /api/v1/feed/categories/{category}
 * Query Params: cursor, limit (default 20)
 *
 * @param category 카테고리
 * @param params 피드 요청 파라미터
 * @returns 피드 응답 (커서 기반 페이지네이션)
 */
export const getCategoryFeed = async (
  category: Category,
  params?: FeedRequest
): Promise<FeedResponse> => {
  const response = await apiClient.get<FeedResponse>(
    API_ENDPOINTS.FEED.CATEGORY(category.toLowerCase()),
    {
      params: {
        cursor: params?.cursor || undefined,
        limit: params?.limit || 20,
      },
    }
  );
  return response.data;
};

/**
 * 특정 콘텐츠 조회
 *
 * 백엔드: GET /api/v1/contents/:contentId
 *
 * @param contentId 콘텐츠 ID
 * @returns 콘텐츠 상세 정보
 */
export const getContentById = async (contentId: string): Promise<FeedItem> => {
  const response = await apiClient.get<FeedItem>(`/api/v1/contents/${contentId}`);
  return response.data;
};
