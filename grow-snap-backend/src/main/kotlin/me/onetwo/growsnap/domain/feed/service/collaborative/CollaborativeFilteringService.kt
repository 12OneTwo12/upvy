package me.onetwo.growsnap.domain.feed.service.collaborative

import me.onetwo.growsnap.domain.content.model.Category
import reactor.core.publisher.Flux
import java.util.UUID

/**
 * 협업 필터링 서비스
 *
 * Item-based Collaborative Filtering 알고리즘을 사용하여
 * 사용자에게 콘텐츠를 추천합니다.
 *
 * ## 협업 필터링 (Collaborative Filtering) 이란?
 *
 * "나와 비슷한 취향을 가진 사용자들이 좋아한 콘텐츠를 나도 좋아할 것이다"
 *
 * ### Item-based CF (아이템 기반 협업 필터링)
 * - Netflix, YouTube, TikTok 등에서 사용하는 방식
 * - "이 콘텐츠를 좋아한 사람들은 이것도 좋아했습니다"
 * - User-based CF보다 확장성과 성능이 우수
 *
 * ## 알고리즘 처리 흐름
 *
 * ```
 * 1. 내가 좋아요/저장/공유한 콘텐츠 조회 (seed items)
 * 2. 각 seed item에 대해:
 *    - 이 콘텐츠를 좋아한 다른 사용자 찾기 (최대 50명)
 *    - 그 사용자들이 좋아한 다른 콘텐츠 찾기 (각 사용자당 20개)
 * 3. 콘텐츠별 점수 계산:
 *    - 기본: 공통 사용자 수 (빈도)
 *    - 가중치: 인터랙션 타입별 (LIKE=1.0, SAVE=1.5, SHARE=2.0)
 * 4. 이미 본/좋아요한 콘텐츠 제외
 * 5. 점수 높은 순으로 정렬하여 반환
 * ```
 *
 * ## 예시
 *
 * ```
 * User A: 콘텐츠1(좋아요), 콘텐츠2(저장)
 * User B: 콘텐츠1(좋아요), 콘텐츠3(좋아요)
 * User C: 콘텐츠2(저장), 콘텐츠4(공유)
 *
 * User A에게 추천:
 * - 콘텐츠3 (User B가 콘텐츠1을 좋아함)
 * - 콘텐츠4 (User C가 콘텐츠2를 저장함)
 * ```
 *
 * ## Best Practice 근거
 *
 * - **Netflix**: Item-based CF + Deep Learning Hybrid
 * - **YouTube**: 사용자 행동 기반 유사도 계산
 * - **TikTok**: 사용자를 "taste clusters"로 그룹화
 */
interface CollaborativeFilteringService {

    /**
     * 협업 필터링 기반 콘텐츠 추천
     *
     * Item-based Collaborative Filtering을 사용하여
     * 사용자가 좋아할 만한 콘텐츠를 추천합니다.
     *
     * ### 알고리즘 (Issue #107: 언어 가중치 적용)
     * 1. 내가 인터랙션한 콘텐츠 조회 (seed items)
     * 2. 각 seed item을 좋아한 다른 사용자 찾기
     * 3. 그 사용자들이 좋아한 콘텐츠 중 내가 안 본 것 추천
     * 4. 카테고리 필터링 (category 포함 / excludeCategory 제외)
     * 5. CF 점수 계산 (공통 사용자가 많을수록 높은 점수)
     * 6. 언어 가중치 적용: final_score = cf_score × language_multiplier
     * 7. 최종 점수 순으로 정렬
     *
     * @param userId 사용자 ID
     * @param limit 추천 개수
     * @param preferredLanguage 사용자 선호 언어 (ISO 639-1, 예: ko, en) - 기본값: "en"
     * @param category 포함할 카테고리 (null이면 전체)
     * @param excludeCategory 제외할 카테고리 (null이면 제외 없음)
     * @return 추천 콘텐츠 ID 목록 (최종 점수 높은 순)
     */
    fun getRecommendedContents(
        userId: UUID,
        limit: Int,
        preferredLanguage: String,
        category: Category? = null,
        excludeCategory: Category? = null
    ): Flux<UUID>
}
