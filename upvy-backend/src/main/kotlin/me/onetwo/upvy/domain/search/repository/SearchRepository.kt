package me.onetwo.upvy.domain.search.repository

import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.domain.content.model.DifficultyLevel
import me.onetwo.upvy.domain.search.dto.AutocompleteSuggestion
import me.onetwo.upvy.domain.search.model.SearchSortType
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 검색 Repository
 *
 * Manticore Search를 사용한 콘텐츠 및 사용자 검색 기능을 제공합니다.
 *
 * ## Manticore Search 인덱스
 * - **content_index**: 콘텐츠 검색 (제목, 설명, 태그, 크리에이터)
 * - **autocomplete_index**: 자동완성 (콘텐츠 + 사용자)
 *
 * ## 검색 흐름
 * 1. Manticore Search에서 검색 (contentId 또는 userId 목록 반환)
 * 2. Service에서 PostgreSQL로 상세 데이터 조회
 * 3. FeedItemResponse 또는 UserSearchResult로 변환
 */
interface SearchRepository {

    /**
     * 콘텐츠 검색
     *
     * Manticore Search content_index에서 콘텐츠를 검색합니다.
     *
     * ## 검색 대상
     * - 제목 (title)
     * - 설명 (description)
     * - 태그 (tags)
     * - 크리에이터 이름 (creator_name)
     *
     * @param query 검색 키워드
     * @param category 카테고리 필터 (선택)
     * @param difficulty 난이도 필터 (선택)
     * @param minDuration 최소 길이 (초, 선택)
     * @param maxDuration 최대 길이 (초, 선택)
     * @param startDate 시작 날짜 (선택)
     * @param endDate 종료 날짜 (선택)
     * @param language 언어 코드 (선택)
     * @param sortBy 정렬 기준
     * @param cursor 페이지네이션 커서 (선택)
     * @param limit 페이지 크기
     * @return 콘텐츠 ID 목록
     */
    fun searchContents(
        query: String,
        category: Category?,
        difficulty: DifficultyLevel?,
        minDuration: Int?,
        maxDuration: Int?,
        startDate: Instant?,
        endDate: Instant?,
        language: String?,
        sortBy: SearchSortType,
        cursor: String?,
        limit: Int
    ): Mono<List<UUID>>

    /**
     * 사용자 검색
     *
     * user_profiles 테이블에서 사용자를 검색합니다.
     *
     * ## 검색 대상
     * - 닉네임 (nickname)
     *
     * @param query 검색 키워드
     * @param cursor 페이지네이션 커서 (선택)
     * @param limit 페이지 크기
     * @return 사용자 ID 목록
     */
    fun searchUsers(
        query: String,
        cursor: String?,
        limit: Int
    ): Mono<List<UUID>>

    /**
     * 자동완성
     *
     * Manticore Search autocomplete_index에서 자동완성 제안을 조회합니다.
     *
     * ## 제안 타입
     * - CONTENT: 콘텐츠 제목
     * - USER: 사용자 닉네임
     *
     * @param query 입력 중인 키워드
     * @param limit 제안 개수
     * @return 자동완성 제안 목록
     */
    fun autocomplete(
        query: String,
        limit: Int
    ): Mono<List<AutocompleteSuggestion>>
}
