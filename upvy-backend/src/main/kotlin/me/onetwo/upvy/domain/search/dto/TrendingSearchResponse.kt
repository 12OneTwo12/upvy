package me.onetwo.upvy.domain.search.dto

/**
 * 인기 검색어 응답 DTO
 *
 * 현재 인기 있는 검색 키워드 목록을 포함합니다.
 *
 * ## 인기 검색어 집계
 * - Redis Sorted Set 사용
 * - Key: `trending:search:keywords`
 * - Score: 검색 횟수
 * - TTL: 1시간
 *
 * ## 정렬 기준
 * - 검색 횟수(searchCount) 내림차순
 * - 순위(rank) 오름차순
 *
 * @property keywords 인기 검색어 목록
 */
data class TrendingSearchResponse(
    val keywords: List<TrendingKeyword>
) {
    companion object {
        /**
         * 빈 응답 생성
         */
        fun empty(): TrendingSearchResponse {
            return TrendingSearchResponse(emptyList())
        }
    }
}

/**
 * 인기 검색어 항목
 *
 * @property keyword 검색 키워드
 * @property searchCount 검색 횟수
 * @property rank 순위 (1부터 시작)
 */
data class TrendingKeyword(
    val keyword: String,
    val searchCount: Int,
    val rank: Int
)
