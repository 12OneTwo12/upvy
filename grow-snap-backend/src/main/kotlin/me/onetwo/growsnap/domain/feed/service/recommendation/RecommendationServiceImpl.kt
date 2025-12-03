package me.onetwo.growsnap.domain.feed.service.recommendation

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.feed.repository.FeedRepository
import me.onetwo.growsnap.domain.feed.service.collaborative.CollaborativeFilteringService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 추천 서비스 구현체
 *
 * 요구사항 명세서 섹션 2.2.1의 추천 알고리즘을 구현합니다.
 * - 협업 필터링 (40%): Item-based Collaborative Filtering
 * - 인기 콘텐츠 (30%): 높은 인터랙션
 * - 신규 콘텐츠 (10%): 최근 업로드
 * - 랜덤 콘텐츠 (20%): 다양성 확보
 *
 * @property feedRepository 피드 데이터 조회를 위한 레포지토리
 * @property collaborativeFilteringService Item-based 협업 필터링 서비스
 */
@Service
@Transactional(readOnly = true)
class RecommendationServiceImpl(
    private val feedRepository: FeedRepository,
    private val collaborativeFilteringService: CollaborativeFilteringService
) : RecommendationService {

    /**
     * 추천 콘텐츠 ID 목록 조회
     *
     * 여러 추천 전략을 병렬로 실행하여 콘텐츠 ID 목록을 반환합니다.
     *
     * ### 처리 흐름
     * 1. 각 전략별로 가져올 콘텐츠 수 계산
     * 2. **병렬로** 각 전략별 콘텐츠 조회 (Mono.zip 사용)
     * 3. 결과 합치기 및 무작위 섞기
     *
     * ### 성능 최적화
     * - Mono.zip을 사용하여 4개 전략을 동시에 실행
     * - 순차 처리(Flux.concat) 대비 약 4배 빠른 응답 시간
     *
     * @param userId 사용자 ID
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @return 추천 콘텐츠 ID 목록 (순서 무작위)
     */
    override fun getRecommendedContentIds(
        userId: UUID,
        limit: Int,
        excludeContentIds: List<UUID>
    ): Flux<UUID> {
        val strategyLimits = RecommendationStrategy.calculateLimits(limit)

        // 병렬로 모든 전략 실행
        return Mono.zip(
            getCollaborativeContentIds(userId, strategyLimits[RecommendationStrategy.COLLABORATIVE]!!, excludeContentIds).collectList(),
            getPopularContentIds(strategyLimits[RecommendationStrategy.POPULAR]!!, excludeContentIds).collectList(),
            getNewContentIds(strategyLimits[RecommendationStrategy.NEW]!!, excludeContentIds).collectList(),
            getRandomContentIds(strategyLimits[RecommendationStrategy.RANDOM]!!, excludeContentIds).collectList()
        ).flatMapMany { tuple ->
            // 모든 결과 합치기 및 무작위 섞기
            val allIds = tuple.t1 + tuple.t2 + tuple.t3 + tuple.t4
            Flux.fromIterable(allIds.shuffled())
        }
    }

    /**
     * 협업 필터링 콘텐츠 ID 조회
     *
     * **Item-based Collaborative Filtering (Issue #10)**
     *
     * "이 콘텐츠를 좋아한 사람들은 이것도 좋아했습니다"
     *
     * ### 처리 흐름
     * 1. Item-based CF 알고리즘으로 추천 콘텐츠 조회
     *    - 내가 좋아요/저장/공유한 콘텐츠 조회 (seed items)
     *    - 각 seed item을 좋아한 다른 사용자 찾기
     *    - 그 사용자들이 좋아한 다른 콘텐츠 추천
     * 2. 추천 결과가 없으면 (신규 사용자) 인기 콘텐츠로 fallback
     * 3. 추천 결과가 부족하면 인기 콘텐츠로 보충
     *
     * ### Best Practice 근거
     * - Netflix, YouTube, TikTok 등이 사용하는 방식
     * - User-based CF보다 확장성과 성능이 우수
     * - 실시간 계산 가능 (배치 처리 불필요)
     *
     * ### 향후 개선 계획 (Phase 2)
     * - Cosine Similarity / Jaccard Similarity 적용
     * - Matrix Factorization
     * - Neural Collaborative Filtering
     *
     * @param userId 사용자 ID
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @return 협업 필터링 추천 콘텐츠 ID 목록
     */
    private fun getCollaborativeContentIds(
        userId: UUID,
        limit: Int,
        excludeContentIds: List<UUID>
    ): Flux<UUID> {
        return collaborativeFilteringService.getRecommendedContents(userId, limit)
            .filter { !excludeContentIds.contains(it) }  // 제외 목록 필터링
            .collectList()
            .flatMapMany { recommendedIds ->
                if (recommendedIds.isEmpty()) {
                    // CF 추천 결과가 없으면 (신규 사용자) 인기 콘텐츠로 fallback
                    logger.debug("No CF recommendations for user {}, falling back to popular content", userId)
                    getPopularContentIds(limit, excludeContentIds)
                } else if (recommendedIds.size < limit) {
                    // CF 추천 결과가 부족하면 인기 콘텐츠로 보충
                    val remaining = limit - recommendedIds.size
                    logger.debug(
                        "CF recommendations ({}) < limit ({}), adding {} popular contents",
                        recommendedIds.size,
                        limit,
                        remaining
                    )
                    Flux.concat(
                        Flux.fromIterable(recommendedIds),
                        getPopularContentIds(remaining, excludeContentIds + recommendedIds)
                    )
                } else {
                    // CF 추천 결과가 충분함
                    logger.debug("Returning {} CF recommendations for user {}", recommendedIds.size, userId)
                    Flux.fromIterable(recommendedIds.take(limit))
                }
            }
    }

    /**
     * 인기 콘텐츠 ID 조회
     *
     * 인터랙션 가중치 기반 인기도 점수가 높은 콘텐츠를 조회합니다.
     *
     * ### 인기도 계산 공식
     * ```
     * popularity_score = view_count * 1.0
     *                  + like_count * 5.0
     *                  + comment_count * 3.0
     *                  + save_count * 7.0
     *                  + share_count * 10.0
     * ```
     *
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @return 인기 콘텐츠 ID 목록 (인기도 순 정렬)
     */
    private fun getPopularContentIds(
        limit: Int,
        excludeContentIds: List<UUID>
    ): Flux<UUID> {
        return feedRepository.findPopularContentIds(limit, excludeContentIds, category = null)
    }

    /**
     * 신규 콘텐츠 ID 조회
     *
     * 최근 업로드된 콘텐츠를 조회합니다.
     *
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @return 신규 콘텐츠 ID 목록 (최신순 정렬)
     */
    private fun getNewContentIds(
        limit: Int,
        excludeContentIds: List<UUID>
    ): Flux<UUID> {
        return feedRepository.findNewContentIds(limit, excludeContentIds, category = null)
    }

    /**
     * 랜덤 콘텐츠 ID 조회
     *
     * 무작위 콘텐츠를 조회하여 다양성을 확보합니다.
     *
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @return 랜덤 콘텐츠 ID 목록 (무작위 정렬)
     */
    private fun getRandomContentIds(
        limit: Int,
        excludeContentIds: List<UUID>
    ): Flux<UUID> {
        return feedRepository.findRandomContentIds(limit, excludeContentIds, category = null)
    }

    /**
     * 카테고리별 추천 콘텐츠 ID 목록 조회
     *
     * 특정 카테고리로 필터링된 추천 콘텐츠를 제공합니다.
     * 메인 피드와 동일한 추천 알고리즘을 사용하되, 해당 카테고리만 반환합니다.
     *
     * ### 추천 전략 (메인 피드와 동일한 비율)
     * - 팔로잉 콘텐츠 (40%): 팔로우한 크리에이터의 해당 카테고리 콘텐츠
     * - 인기 콘텐츠 (30%): 해당 카테고리의 인기 콘텐츠
     * - 신규 콘텐츠 (10%): 해당 카테고리의 최신 콘텐츠
     * - 랜덤 콘텐츠 (20%): 해당 카테고리의 랜덤 콘텐츠
     *
     * ### 처리 흐름
     * 1. 각 전략별로 가져올 콘텐츠 수 계산
     * 2. **병렬로** 각 전략별 콘텐츠 조회 (Mono.zip 사용)
     * 3. 결과 합치기 및 무작위 섞기
     *
     * @param userId 사용자 ID
     * @param category 필터링할 카테고리
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @return 추천 콘텐츠 ID 목록 (순서 무작위)
     */
    override fun getRecommendedContentIdsByCategory(
        userId: UUID,
        category: Category,
        limit: Int,
        excludeContentIds: List<UUID>
    ): Flux<UUID> {
        // 메인 피드와 동일한 비율 사용 (COLLABORATIVE 대신 FOLLOWING 사용)
        val strategyLimits = RecommendationStrategy.calculateLimits(limit)

        // 병렬로 모든 전략 실행 (Repository 메서드 직접 호출)
        return Mono.zip(
            getFollowingContentIdsByCategory(userId, category, strategyLimits[RecommendationStrategy.COLLABORATIVE]!!, excludeContentIds).collectList(),
            feedRepository.findPopularContentIds(strategyLimits[RecommendationStrategy.POPULAR]!!, excludeContentIds, category).collectList(),
            feedRepository.findNewContentIds(strategyLimits[RecommendationStrategy.NEW]!!, excludeContentIds, category).collectList(),
            feedRepository.findRandomContentIds(strategyLimits[RecommendationStrategy.RANDOM]!!, excludeContentIds, category).collectList()
        ).flatMapMany { tuple ->
            // 모든 결과 합치기 및 무작위 섞기
            val allIds = tuple.t1 + tuple.t2 + tuple.t3 + tuple.t4
            Flux.fromIterable(allIds.shuffled())
        }
    }

    /**
     * 특정 카테고리의 팔로잉 콘텐츠 ID 조회
     *
     * @param userId 사용자 ID
     * @param category 조회할 카테고리
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @return 팔로잉 콘텐츠 ID 목록 (최신순 정렬)
     */
    private fun getFollowingContentIdsByCategory(
        userId: UUID,
        category: Category,
        limit: Int,
        excludeContentIds: List<UUID>
    ): Flux<UUID> {
        return feedRepository.findFollowingContentIdsByCategory(userId, category, limit, excludeContentIds)
            .collectList()
            .flatMapMany { followingIds ->
                if (followingIds.isEmpty()) {
                    // 팔로잉 콘텐츠가 없으면 인기 콘텐츠로 fallback
                    logger.debug("No following content for user {} in category {}, falling back to popular content", userId, category.name)
                    feedRepository.findPopularContentIds(limit, excludeContentIds, category)
                } else if (followingIds.size < limit) {
                    // 팔로잉 콘텐츠가 부족하면 인기 콘텐츠로 보충
                    val remaining = limit - followingIds.size
                    logger.debug(
                        "Following content ({}) < limit ({}) for category {}, adding {} popular contents",
                        followingIds.size,
                        limit,
                        category.name,
                        remaining
                    )
                    Flux.concat(
                        Flux.fromIterable(followingIds),
                        feedRepository.findPopularContentIds(remaining, excludeContentIds + followingIds, category)
                    )
                } else {
                    // 팔로잉 콘텐츠가 충분함
                    logger.debug("Returning {} following contents for user {} in category {}", followingIds.size, userId, category.name)
                    Flux.fromIterable(followingIds.take(limit))
                }
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RecommendationServiceImpl::class.java)
    }
}
