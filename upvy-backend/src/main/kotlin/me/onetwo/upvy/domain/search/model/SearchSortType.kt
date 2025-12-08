package me.onetwo.upvy.domain.search.model

/**
 * 검색 정렬 기준
 *
 * 콘텐츠 검색 결과의 정렬 방식을 정의합니다.
 * Manticore Search의 정렬 기능을 활용합니다.
 *
 * ## 정렬 방식
 * - **RELEVANCE**: BM25 알고리즘 기반 검색 스코어 정렬 (기본값)
 * - **RECENT**: 최신순 정렬 (created_at DESC)
 * - **POPULAR**: 인기순 정렬 (popularity_score DESC)
 *
 * ## Manticore Search 쿼리 예시
 * - RELEVANCE: `ORDER BY WEIGHT() DESC, created_at DESC`
 * - RECENT: `ORDER BY created_at DESC`
 * - POPULAR: `ORDER BY popularity_score DESC, created_at DESC`
 */
enum class SearchSortType {
    /**
     * 관련도순 정렬
     *
     * BM25 알고리즘 기반 검색 스코어로 정렬합니다.
     * 검색어와 가장 관련성이 높은 콘텐츠가 먼저 표시됩니다.
     */
    RELEVANCE,

    /**
     * 최신순 정렬
     *
     * 콘텐츠 생성 시각(created_at) 기준으로 내림차순 정렬합니다.
     * 최근에 생성된 콘텐츠가 먼저 표시됩니다.
     */
    RECENT,

    /**
     * 인기순 정렬
     *
     * 인기 스코어(popularity_score) 기준으로 내림차순 정렬합니다.
     * 인기 스코어 = (like_count * 2 + view_count + comment_count * 3)
     */
    POPULAR
}
