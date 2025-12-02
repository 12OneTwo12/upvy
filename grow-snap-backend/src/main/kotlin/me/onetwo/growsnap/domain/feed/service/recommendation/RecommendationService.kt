package me.onetwo.growsnap.domain.feed.service.recommendation

import me.onetwo.growsnap.domain.content.model.Category
import reactor.core.publisher.Flux
import java.util.UUID

/**
 * 추천 서비스 인터페이스
 *
 * 콘텐츠 추천 알고리즘을 제공합니다.
 */
interface RecommendationService {

    /**
     * 추천 콘텐츠 ID 목록 조회
     *
     * 여러 추천 전략을 혼합하여 콘텐츠 ID 목록을 반환합니다.
     *
     * @param userId 사용자 ID
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @return 추천 콘텐츠 ID 목록 (순서 무작위)
     */
    fun getRecommendedContentIds(
        userId: UUID,
        limit: Int,
        excludeContentIds: List<UUID>
    ): Flux<UUID>

    /**
     * 카테고리별 추천 콘텐츠 ID 목록 조회
     *
     * 특정 카테고리로 필터링된 추천 콘텐츠를 제공합니다.
     * 메인 피드와 동일한 추천 알고리즘을 사용하되, 해당 카테고리만 반환합니다.
     *
     * @param userId 사용자 ID
     * @param category 필터링할 카테고리
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @return 추천 콘텐츠 ID 목록 (순서 무작위)
     */
    fun getRecommendedContentIdsByCategory(
        userId: UUID,
        category: Category,
        limit: Int,
        excludeContentIds: List<UUID>
    ): Flux<UUID>
}
