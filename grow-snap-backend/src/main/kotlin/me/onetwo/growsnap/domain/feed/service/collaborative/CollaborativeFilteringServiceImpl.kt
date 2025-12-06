package me.onetwo.growsnap.domain.feed.service.collaborative

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.repository.UserContentInteractionRepository
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.exp

/**
 * 협업 필터링 서비스 구현체
 *
 * Item-based Collaborative Filtering 알고리즘을 구현합니다.
 *
 * ## 알고리즘 설명
 *
 * ### Item-based Collaborative Filtering
 * "이 콘텐츠를 좋아한 사람들은 이것도 좋아했습니다"
 *
 * ### 처리 흐름
 * ```
 * 1. 내가 좋아요/저장/공유한 콘텐츠 조회 (seed items)
 *    - 최대 100개 조회
 *
 * 2. 각 seed item에 대해:
 *    - 이 콘텐츠를 좋아한 다른 사용자 찾기 (최대 50명)
 *    - 그 사용자들이 좋아한 다른 콘텐츠 찾기 (각 20개)
 *
 * 3. 콘텐츠별 점수 계산:
 *    - 같은 콘텐츠가 여러 번 등장 = 공통 사용자가 많음
 *    - 인터랙션 타입별 가중치:
 *      * LIKE: 1.0
 *      * SAVE: 1.5 (저장 = 나중에 다시 보고 싶음)
 *      * SHARE: 2.0 (공유 = 타인에게 추천할 만큼 좋음)
 *
 * 4. 필터링:
 *    - 이미 내가 인터랙션한 콘텐츠 제외
 *
 * 5. 정렬 및 반환:
 *    - 점수 높은 순으로 정렬
 *    - 상위 N개 반환
 * ```
 *
 * ### 예시
 * ```
 * User A: Content1(좋아요), Content2(저장)
 * User B: Content1(좋아요), Content3(좋아요), Content5(공유)
 * User C: Content2(저장), Content4(좋아요)
 *
 * User A에게 추천:
 * 1. Content1을 좋아한 User B 찾기
 *    → User B가 좋아한 Content3, Content5 후보
 * 2. Content2를 저장한 User C 찾기
 *    → User C가 좋아한 Content4 후보
 * 3. 점수 계산:
 *    - Content3: 1.0 (LIKE)
 *    - Content5: 2.0 (SHARE)
 *    - Content4: 1.0 (LIKE)
 * 4. 추천: Content5 > Content3 = Content4
 * ```
 *
 * @property userContentInteractionRepository 사용자별 콘텐츠 인터랙션 레포지토리
 * @property contentRepository 콘텐츠 메타데이터 조회를 위한 레포지토리 (언어 가중치)
 */
@Service
@Transactional(readOnly = true)
class CollaborativeFilteringServiceImpl(
    private val userContentInteractionRepository: UserContentInteractionRepository,
    private val contentRepository: ContentRepository
) : CollaborativeFilteringService {

    /**
     * 협업 필터링 기반 콘텐츠 추천
     *
     * Item-based CF 알고리즘을 사용하여 추천합니다.
     * Issue #107: 언어 가중치 적용 및 카테고리 필터링
     * Issue #92: 시간 기반 Decay 적용으로 최신 콘텐츠 우선 추천
     *
     * ### 최종 점수 계산 공식
     * ```
     * final_score = cf_score × language_multiplier × time_decay
     *
     * time_decay = EXP(-0.02 × days_since_created)
     * - 0일: 1.0 (100%)
     * - 14일: ~0.75 (75%)
     * - 30일: ~0.55 (55%)
     * - 60일: ~0.30 (30%)
     * ```
     * CF는 취향 기반이므로 인기 콘텐츠(0.05)보다 완만한 감쇠율(0.02) 적용
     *
     * @param userId 사용자 ID
     * @param limit 추천 개수
     * @param preferredLanguage 사용자 선호 언어 (ISO 639-1, 예: ko, en)
     * @param category 포함할 카테고리 (null이면 전체)
     * @param excludeCategory 제외할 카테고리 (null이면 제외 없음)
     * @return 추천 콘텐츠 ID 목록 (최종 점수 높은 순)
     */
    override fun getRecommendedContents(
        userId: UUID,
        limit: Int,
        preferredLanguage: String,
        category: Category?,
        excludeCategory: Category?
    ): Flux<UUID> {
        // 1. 내가 인터랙션한 콘텐츠 조회 (seed items)
        val myInteractionsMono = userContentInteractionRepository
            .findAllInteractionsByUser(userId, MAX_SEED_ITEMS)
            .collectList()

        return myInteractionsMono.flatMapMany outer@{ myInteractions ->
            if (myInteractions.isEmpty()) {
                logger.debug("No interactions found for user {}", userId)
                return@outer Flux.empty<UUID>()
            }

            val myContentIds = myInteractions.map { it.contentId }.toSet()
            logger.debug("Found {} seed items for user {}", myContentIds.size, userId)

            // 2. 각 seed item을 좋아한 다른 사용자 찾기
            val similarUsersMono = Flux.fromIterable(myContentIds)
                .flatMap { contentId ->
                    userContentInteractionRepository
                        .findUsersByContent(contentId, interactionType = null, limit = MAX_SIMILAR_USERS_PER_ITEM)
                }
                .filter { it != userId }  // 자신 제외
                .distinct()
                .collectList()

            similarUsersMono.flatMapMany inner@{ similarUsers ->
                if (similarUsers.isEmpty()) {
                    logger.debug("No similar users found for user {}", userId)
                    return@inner Flux.empty<UUID>()
                }

                logger.debug("Found {} similar users for user {}", similarUsers.size, userId)

                // 3. 그 사용자들이 좋아한 콘텐츠 찾기 (스트리밍 방식)
                Flux.fromIterable(similarUsers)
                    .flatMap { similarUserId ->
                        userContentInteractionRepository
                            .findAllInteractionsByUser(similarUserId, MAX_ITEMS_PER_SIMILAR_USER)
                    }
                    // 4. 이미 내가 인터랙션한 콘텐츠 제외
                    .filter { interaction -> !myContentIds.contains(interaction.contentId) }
                    // 5. contentId로 그룹화하여 점수 계산 (메모리 효율적)
                    .groupBy { it.contentId }  // contentId로 그룹화
                    .flatMap { group ->
                        group.reduce(0.0) { score, interaction ->
                            val weight = when (interaction.interactionType) {
                                InteractionType.LIKE -> LIKE_WEIGHT
                                InteractionType.SAVE -> SAVE_WEIGHT
                                InteractionType.SHARE -> SHARE_WEIGHT
                                InteractionType.COMMENT -> 0.0  // COMMENT는 점수에 포함 안 함
                            }
                            score + weight
                        }.map { totalScore -> group.key() to totalScore }
                    }
                    // 6. 점수가 0보다 큰 것만 필터링 (COMMENT 제외)
                    .filter { (_, score) -> score > 0.0 }
                    // 7. CF 점수 계산 완료
                    .collectList()
                    .flatMapMany { cfScoreList ->
                        logger.debug(
                            "Calculated CF scores for {} candidate contents for user {}",
                            cfScoreList.size,
                            userId
                        )

                        if (cfScoreList.isEmpty()) {
                            return@flatMapMany Flux.empty<UUID>()
                        }

                        // 8. 메타데이터 조회 (언어 및 카테고리) (Issue #107)
                        val candidateIds = cfScoreList.map { it.first }
                        contentRepository.findByIdsWithMetadata(candidateIds)
                            .collectMap({ it.content.id!! }, { it.metadata })
                            .flatMapMany { metadataMap ->
                                // 9. 카테고리 필터링, 언어 가중치, 시간 감쇠 적용하여 최종 점수 계산
                                val now = Instant.now()
                                val finalScores = cfScoreList.mapNotNull { (contentId, cfScore) ->
                                    val metadata = metadataMap[contentId] ?: return@mapNotNull null

                                    // 카테고리 포함 필터링 (category가 지정된 경우)
                                    if (category != null && metadata.category != category) {
                                        return@mapNotNull null
                                    }

                                    // 카테고리 제외 필터링 (excludeCategory가 지정된 경우)
                                    if (excludeCategory != null && metadata.category == excludeCategory) {
                                        return@mapNotNull null
                                    }

                                    // 언어 가중치 적용
                                    val languageMultiplier = if (metadata.language == preferredLanguage) {
                                        LANGUAGE_MULTIPLIER_MATCH
                                    } else {
                                        LANGUAGE_MULTIPLIER_MISMATCH
                                    }

                                    // 시간 기반 Decay 적용 (Issue #92)
                                    val timeDecay = calculateTimeDecay(metadata.createdAt, now)

                                    // 최종 점수 = CF 점수 × 언어 가중치 × 시간 감쇠
                                    val finalScore = cfScore * languageMultiplier * timeDecay
                                    contentId to finalScore
                                }

                                // 10. 최종 점수 높은 순으로 정렬하여 상위 N개 반환
                                val recommendedContents = finalScores
                                    .sortedByDescending { it.second }
                                    .take(limit)
                                    .map { it.first }

                                logger.debug(
                                    "Returning {} recommended contents (with language weighting, time decay, and category filtering) for user {}{}{}",
                                    recommendedContents.size,
                                    userId,
                                    if (category != null) " in category ${category.name}" else "",
                                    if (excludeCategory != null) " excluding ${excludeCategory.name}" else ""
                                )

                                Flux.fromIterable(recommendedContents)
                            }
                    }
            }
        }
    }

    /**
     * 시간 기반 Decay 계산
     *
     * 콘텐츠 생성일로부터의 경과일 기반 감쇠를 계산합니다.
     * 오래된 콘텐츠일수록 점수가 낮아져 최신 콘텐츠가 우선 추천됩니다.
     *
     * ### Decay 공식 (CF용 완만한 감쇠)
     * ```
     * time_decay = EXP(-0.02 * days_since_created)
     * ```
     * - 0일: 1.0 (100%)
     * - 14일: ~0.75 (75%)
     * - 30일: ~0.55 (55%)
     * - 60일: ~0.30 (30%)
     *
     * @param createdAt 콘텐츠 생성 시각
     * @param now 현재 시각
     * @return 시간 감쇠 계수 (0.0 ~ 1.0)
     */
    private fun calculateTimeDecay(createdAt: Instant, now: Instant): Double {
        val daysSinceCreated = ChronoUnit.DAYS.between(createdAt, now).toDouble()
        return exp(-TIME_DECAY_RATE * daysSinceCreated)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CollaborativeFilteringServiceImpl::class.java)

        /**
         * 분석할 최대 seed item 수
         */
        private const val MAX_SEED_ITEMS = 100

        /**
         * 각 seed item당 찾을 최대 유사 사용자 수
         */
        private const val MAX_SIMILAR_USERS_PER_ITEM = 50

        /**
         * 각 유사 사용자가 좋아한 콘텐츠 조회 개수
         */
        private const val MAX_ITEMS_PER_SIMILAR_USER = 20

        /**
         * 인터랙션 타입별 가중치
         */
        private const val LIKE_WEIGHT = 1.0
        private const val SAVE_WEIGHT = 1.5
        private const val SHARE_WEIGHT = 2.0

        /**
         * 언어 가중치 (Issue #107)
         *
         * 사용자 선호 언어에 따라 CF 점수에 적용되는 가중치입니다.
         * - 일치: 2.0x (우선 추천)
         * - 불일치: 0.5x (후순위 추천)
         */
        private const val LANGUAGE_MULTIPLIER_MATCH = 2.0
        private const val LANGUAGE_MULTIPLIER_MISMATCH = 0.5

        /**
         * 시간 기반 Decay 감쇠율 (Issue #92)
         *
         * EXP(-rate * days) 공식에 사용되는 감쇠율입니다.
         * CF 추천은 취향 기반이므로 인기 콘텐츠(0.05)보다 완만한 감쇠 적용:
         * - 0.02: 14일 후 약 75%, 30일 후 약 55%, 60일 후 약 30%
         *
         * 오래된 "클래식" 콘텐츠도 추천 가치가 있으므로 너무 공격적인 감쇠 회피
         */
        private const val TIME_DECAY_RATE = 0.02
    }
}
