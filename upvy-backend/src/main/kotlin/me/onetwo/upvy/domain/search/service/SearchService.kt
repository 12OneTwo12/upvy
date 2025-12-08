package me.onetwo.upvy.domain.search.service

import me.onetwo.upvy.domain.search.dto.AutocompleteRequest
import me.onetwo.upvy.domain.search.dto.AutocompleteResponse
import me.onetwo.upvy.domain.search.dto.ContentSearchRequest
import me.onetwo.upvy.domain.search.dto.ContentSearchResponse
import me.onetwo.upvy.domain.search.dto.SearchHistoryResponse
import me.onetwo.upvy.domain.search.dto.TrendingSearchResponse
import me.onetwo.upvy.domain.search.dto.UserSearchRequest
import me.onetwo.upvy.domain.search.dto.UserSearchResponse
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 검색 Service
 *
 * 콘텐츠, 사용자 검색 및 자동완성, 인기 검색어 기능을 제공합니다.
 *
 * ## 검색 흐름
 * 1. SearchRepository에서 검색 (contentId 또는 userId 목록)
 * 2. PostgreSQL에서 상세 데이터 조회
 * 3. Response DTO로 변환
 */
interface SearchService {

    /**
     * 콘텐츠 검색
     *
     * 콘텐츠 제목, 설명, 태그, 크리에이터 이름에서 검색합니다.
     * FeedItemResponse 형식으로 반환하여 기존 피드 UI를 재사용할 수 있습니다.
     *
     * @param request 콘텐츠 검색 요청
     * @param userId 현재 사용자 ID (인터랙션 정보 조회용, 선택)
     * @return 콘텐츠 검색 응답
     */
    fun searchContents(
        request: ContentSearchRequest,
        userId: UUID?
    ): Mono<ContentSearchResponse>

    /**
     * 사용자 검색
     *
     * 사용자 닉네임에서 검색합니다.
     *
     * @param request 사용자 검색 요청
     * @param currentUserId 현재 사용자 ID (팔로우 여부 확인용, 선택)
     * @return 사용자 검색 응답
     */
    fun searchUsers(
        request: UserSearchRequest,
        currentUserId: UUID?
    ): Mono<UserSearchResponse>

    /**
     * 자동완성
     *
     * 입력 중인 키워드에 대한 자동완성 제안을 반환합니다.
     *
     * @param request 자동완성 요청
     * @return 자동완성 응답
     */
    fun autocomplete(
        request: AutocompleteRequest
    ): Mono<AutocompleteResponse>

    /**
     * 인기 검색어
     *
     * 현재 인기 있는 검색 키워드 목록을 반환합니다.
     *
     * @param limit 인기 검색어 개수 (기본값: 10)
     * @return 인기 검색어 응답
     */
    fun getTrendingKeywords(
        limit: Int = 10
    ): Mono<TrendingSearchResponse>

    /**
     * 최근 검색어 조회
     *
     * 사용자의 최근 검색어 목록을 반환합니다.
     * 동일한 키워드는 가장 최근 검색만 표시됩니다.
     *
     * @param userId 사용자 ID
     * @param limit 최대 개수 (기본값: 10)
     * @return 검색 기록 응답
     */
    fun getRecentSearches(
        userId: UUID,
        limit: Int = 10
    ): Mono<SearchHistoryResponse>

    /**
     * 특정 검색어 삭제
     *
     * @param userId 사용자 ID
     * @param keyword 검색 키워드
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    fun deleteSearchHistory(
        userId: UUID,
        keyword: String
    ): Mono<Void>

    /**
     * 전체 검색 기록 삭제
     *
     * @param userId 사용자 ID
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    fun deleteAllSearchHistory(
        userId: UUID
    ): Mono<Void>
}
