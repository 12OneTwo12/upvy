/**
 * Search API with Analytics
 *
 * 검색 API 호출 시 Analytics 이벤트를 자동으로 로깅하는 wrapper 함수들
 * - API 레이어와 Analytics를 분리하기 위해 별도 파일로 관리
 * - Fire-and-Forget 패턴 사용 (Analytics 호출에 await 없음)
 */

import { searchContents, searchUsers, type SearchRequest } from '@/api/search.api';
import { Analytics } from '@/utils/analytics';

/**
 * 콘텐츠 검색 (Analytics 포함)
 * @param request 검색 요청
 * @returns 검색 결과
 */
export const searchContentsWithAnalytics = async (request: SearchRequest) => {
  const result = await searchContents(request);

  // Analytics 이벤트 (Fire-and-Forget - await 없음)
  if (request.q) {
    const resultCount = result.contents?.length || 0;
    Analytics.logSearch(request.q, resultCount, request.category);
  }

  return result;
};

/**
 * 사용자 검색 (Analytics 포함)
 * @param request 검색 요청
 * @returns 검색 결과
 */
export const searchUsersWithAnalytics = async (request: SearchRequest) => {
  const result = await searchUsers(request);

  // Analytics 이벤트 (Fire-and-Forget - await 없음)
  if (request.q) {
    const resultCount = result.users?.length || 0;
    Analytics.logSearch(request.q, resultCount, 'users');
  }

  return result;
};
