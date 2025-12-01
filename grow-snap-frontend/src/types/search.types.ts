/**
 * 검색 관련 타입 정의
 *
 * 백엔드 API 스펙과 100% 일치:
 * - grow-snap-backend/src/main/kotlin/me/onetwo/growsnap/domain/search/dto/
 * - grow-snap-backend/src/main/kotlin/me/onetwo/growsnap/domain/search/model/
 */

import type { Category, FeedItem, CursorPageResponse } from './feed.types';

/**
 * 검색 정렬 기준
 * 백엔드: me.onetwo.growsnap.domain.search.model.SearchSortType
 */
export type SearchSortType = 'RELEVANCE' | 'RECENT' | 'POPULAR';

/**
 * 검색 타입
 * 백엔드: me.onetwo.growsnap.domain.search.model.SearchType
 */
export type SearchType = 'CONTENT' | 'USER';

/**
 * 난이도 레벨
 * 백엔드: me.onetwo.growsnap.domain.content.model.DifficultyLevel
 */
export type DifficultyLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';

/**
 * 자동완성 제안 타입
 * 백엔드: me.onetwo.growsnap.domain.search.dto.SuggestionType
 */
export type SuggestionType = 'CONTENT' | 'TAG' | 'USER';

// ===== 콘텐츠 검색 =====

/**
 * 콘텐츠 검색 요청
 * 백엔드: ContentSearchRequest
 */
export interface ContentSearchRequest {
  q: string;
  category?: Category;
  difficulty?: DifficultyLevel;
  minDuration?: number;
  maxDuration?: number;
  startDate?: string; // ISO 8601 형식 (YYYY-MM-DD)
  endDate?: string; // ISO 8601 형식 (YYYY-MM-DD)
  language?: string; // 2글자 언어 코드 (예: "ko", "en")
  sortBy?: SearchSortType;
  cursor?: string;
  limit?: number;
}

/**
 * 콘텐츠 검색 응답
 * 백엔드: ContentSearchResponse = CursorPageResponse<FeedItemResponse>
 */
export type ContentSearchResponse = CursorPageResponse<FeedItem>;

// ===== 사용자 검색 =====

/**
 * 사용자 검색 요청
 * 백엔드: UserSearchRequest
 */
export interface UserSearchRequest {
  q: string;
  cursor?: string;
  limit?: number;
}

/**
 * 사용자 검색 결과 항목
 * 백엔드: UserSearchResult
 */
export interface UserSearchResult {
  userId: string;
  nickname: string;
  profileImageUrl: string | null;
  bio: string | null;
  followerCount: number;
  isFollowing: boolean;
}

/**
 * 사용자 검색 응답
 * 백엔드: UserSearchResponse = CursorPageResponse<UserSearchResult>
 */
export type UserSearchResponse = CursorPageResponse<UserSearchResult>;

// ===== 자동완성 =====

/**
 * 자동완성 요청
 * 백엔드: AutocompleteRequest
 */
export interface AutocompleteRequest {
  q: string;
  limit?: number;
}

/**
 * 자동완성 제안 항목
 * 백엔드: AutocompleteSuggestion
 */
export interface AutocompleteSuggestion {
  text: string;
  type: SuggestionType;
  highlightedText: string;
}

/**
 * 자동완성 응답
 * 백엔드: AutocompleteResponse
 */
export interface AutocompleteResponse {
  suggestions: AutocompleteSuggestion[];
}

// ===== 인기 검색어 =====

/**
 * 인기 검색어 항목
 * 백엔드: TrendingKeyword
 */
export interface TrendingKeyword {
  keyword: string;
  searchCount: number;
  rank: number;
}

/**
 * 인기 검색어 응답
 * 백엔드: TrendingSearchResponse
 */
export interface TrendingSearchResponse {
  keywords: TrendingKeyword[];
}

// ===== 검색 기록 =====

/**
 * 검색 기록 항목
 * 백엔드: SearchHistoryItem
 */
export interface SearchHistoryItem {
  keyword: string;
  searchType: SearchType;
}

/**
 * 검색 기록 응답
 * 백엔드: SearchHistoryResponse
 */
export interface SearchHistoryResponse {
  keywords: SearchHistoryItem[];
}
