/**
 * 검색 API 클라이언트
 *
 * 백엔드 API 스펙:
 * - upvy-backend/src/main/kotlin/me/onetwo/upvy/domain/search/controller/SearchController.kt
 */

import apiClient from './client';
import { API_ENDPOINTS } from '@/constants/api';
import type {
  ContentSearchRequest,
  ContentSearchResponse,
  UserSearchRequest,
  UserSearchResponse,
  AutocompleteRequest,
  AutocompleteResponse,
  TrendingSearchResponse,
  SearchHistoryResponse,
} from '@/types/search.types';

/**
 * 콘텐츠 검색
 *
 * 백엔드: GET /api/v1/search/contents
 * Query Params: q (필수), category, difficulty, minDuration, maxDuration,
 *               startDate, endDate, language, sortBy, cursor, limit
 *
 * @param params 콘텐츠 검색 요청 파라미터
 * @returns 콘텐츠 검색 결과 (커서 기반 페이지네이션)
 */
export const searchContents = async (
  params: ContentSearchRequest
): Promise<ContentSearchResponse> => {
  const response = await apiClient.get<ContentSearchResponse>(API_ENDPOINTS.SEARCH.CONTENTS, {
    params: {
      q: params.q,
      category: params.category || undefined,
      difficulty: params.difficulty || undefined,
      minDuration: params.minDuration || undefined,
      maxDuration: params.maxDuration || undefined,
      startDate: params.startDate || undefined,
      endDate: params.endDate || undefined,
      language: params.language || undefined,
      sortBy: params.sortBy || 'RELEVANCE',
      cursor: params.cursor || undefined,
      limit: params.limit || 20,
    },
  });
  return response.data;
};

/**
 * 사용자 검색
 *
 * 백엔드: GET /api/v1/search/users
 * Query Params: q (필수), cursor, limit
 *
 * @param params 사용자 검색 요청 파라미터
 * @returns 사용자 검색 결과 (커서 기반 페이지네이션)
 */
export const searchUsers = async (params: UserSearchRequest): Promise<UserSearchResponse> => {
  const response = await apiClient.get<UserSearchResponse>(API_ENDPOINTS.SEARCH.USERS, {
    params: {
      q: params.q,
      cursor: params.cursor || undefined,
      limit: params.limit || 20,
    },
  });
  return response.data;
};

/**
 * 자동완성
 *
 * 백엔드: GET /api/v1/search/autocomplete
 * Query Params: q (필수), limit
 *
 * @param params 자동완성 요청 파라미터
 * @returns 자동완성 제안 목록
 */
export const autocomplete = async (
  params: AutocompleteRequest
): Promise<AutocompleteResponse> => {
  const response = await apiClient.get<AutocompleteResponse>(API_ENDPOINTS.SEARCH.AUTOCOMPLETE, {
    params: {
      q: params.q,
      limit: params.limit || 10,
    },
  });
  return response.data;
};

/**
 * 인기 검색어 조회
 *
 * 백엔드: GET /api/v1/search/trending
 * Query Params: limit (기본값: 10)
 *
 * @param limit 인기 검색어 개수
 * @returns 인기 검색어 목록
 */
export const getTrendingKeywords = async (limit: number = 10): Promise<TrendingSearchResponse> => {
  const response = await apiClient.get<TrendingSearchResponse>(API_ENDPOINTS.SEARCH.TRENDING, {
    params: { limit },
  });
  return response.data;
};

/**
 * 검색 기록 조회
 *
 * 백엔드: GET /api/v1/search/history
 * Query Params: limit (기본값: 10)
 *
 * @param limit 최대 검색 기록 개수
 * @returns 검색 기록 목록
 */
export const getSearchHistory = async (limit: number = 10): Promise<SearchHistoryResponse> => {
  const response = await apiClient.get<SearchHistoryResponse>(API_ENDPOINTS.SEARCH.HISTORY, {
    params: { limit },
  });
  return response.data;
};

/**
 * 특정 검색어 삭제
 *
 * 백엔드: DELETE /api/v1/search/history/{keyword}
 * Response: 204 No Content
 *
 * @param keyword 삭제할 검색 키워드
 */
export const deleteSearchHistory = async (keyword: string): Promise<void> => {
  await apiClient.delete(API_ENDPOINTS.SEARCH.DELETE_HISTORY(keyword));
};

/**
 * 전체 검색 기록 삭제
 *
 * 백엔드: DELETE /api/v1/search/history
 * Response: 204 No Content
 */
export const deleteAllSearchHistory = async (): Promise<void> => {
  await apiClient.delete(API_ENDPOINTS.SEARCH.HISTORY);
};
