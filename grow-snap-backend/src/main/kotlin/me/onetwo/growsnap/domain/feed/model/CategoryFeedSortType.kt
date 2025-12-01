package me.onetwo.growsnap.domain.feed.model

/**
 * 카테고리 피드 정렬 타입
 *
 * 카테고리별 피드 조회 시 사용되는 정렬 옵션입니다.
 *
 * @property POPULAR 인기순 - 인터랙션 가중치 기반 인기도 점수로 정렬
 * @property RECENT 최신순 - 생성일자 기준 최신 콘텐츠 우선 정렬
 */
enum class CategoryFeedSortType {
    /**
     * 인기순 정렬
     *
     * ### 인기도 계산 공식
     * ```
     * popularity_score = view_count * 1.0
     *                  + like_count * 5.0
     *                  + comment_count * 3.0
     *                  + save_count * 7.0
     *                  + share_count * 10.0
     * ```
     */
    POPULAR,

    /**
     * 최신순 정렬
     *
     * 콘텐츠 생성일자(created_at) 기준 내림차순 정렬
     */
    RECENT
}
