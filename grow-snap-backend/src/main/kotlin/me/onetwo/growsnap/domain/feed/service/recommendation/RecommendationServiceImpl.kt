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
 *
 * ## 추천 전략 (비율)
 * - 협업 필터링 (40%): Item-based Collaborative Filtering
 * - 인기 콘텐츠 (30%): 높은 인터랙션
 * - 신규 콘텐츠 (10%): 최근 업로드
 * - 랜덤 콘텐츠 (20%): 다양성 확보
 *
 * ## Issue #92: FUN 카테고리 믹싱
 * 전체 피드에서 20%는 FUN 카테고리로 구성하되, FUN 콘텐츠에도 동일한 추천 전략 적용
 *
 * ## Issue #107: 언어 기반 가중치 적용
 * Repository 레벨에서 SQL CASE WHEN을 통해 언어 가중치 적용
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
     * @param userId 사용자 ID
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @param preferredLanguage 사용자 선호 언어 (ISO 639-1)
     * @param category 필터링할 카테고리 (null이면 전체 피드 + FUN 믹싱)
     * @return 추천 콘텐츠 ID 목록
     */
    override fun getRecommendedContentIds(
        userId: UUID,
        limit: Int,
        excludeContentIds: List<UUID>,
        preferredLanguage: String,
        category: Category?
    ): Flux<UUID> {
        // 특정 카테고리 조회 시: FUN 믹싱 없이 해당 카테고리만
        if (category != null) {
            return fetchContentsByAllStrategies(userId, limit, excludeContentIds, preferredLanguage, category)
                .flatMapMany { Flux.fromIterable(it) }
        }

        // 전체 피드: FUN 믹싱 적용 (Issue #92)
        return getRecommendedContentIdsWithFunMixing(userId, limit, excludeContentIds, preferredLanguage)
    }

    /**
     * 모든 추천 전략을 적용하여 콘텐츠 ID 목록 조회
     *
     * **핵심 로직을 하나의 메서드로 추출하여 중복 제거 (DRY 원칙)**
     *
     * 협업 필터링, 인기, 신규, 랜덤 4가지 전략을 병렬로 실행하고 결과를 합칩니다.
     * 일반 콘텐츠와 FUN 콘텐츠 모두 이 메서드를 통해 동일한 추천 품질을 보장합니다.
     *
     * @param userId 사용자 ID
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @param preferredLanguage 사용자 선호 언어
     * @param category 포함할 카테고리 (null이면 전체)
     * @param excludeCategory 제외할 카테고리 (null이면 제외 없음)
     * @return 셔플된 콘텐츠 ID 목록
     */
    private fun fetchContentsByAllStrategies(
        userId: UUID,
        limit: Int,
        excludeContentIds: List<UUID>,
        preferredLanguage: String,
        category: Category? = null,
        excludeCategory: Category? = null
    ): Mono<List<UUID>> {
        val strategyLimits = RecommendationStrategy.calculateLimits(limit)

        return Mono.zip(
            getCollaborativeContentIds(
                userId,
                strategyLimits[RecommendationStrategy.COLLABORATIVE]!!,
                excludeContentIds,
                preferredLanguage,
                category,
                excludeCategory
            ).collectList(),
            feedRepository.findPopularContentIds(
                userId,
                strategyLimits[RecommendationStrategy.POPULAR]!!,
                excludeContentIds,
                category,
                preferredLanguage,
                excludeCategory
            ).collectList(),
            feedRepository.findNewContentIds(
                userId,
                strategyLimits[RecommendationStrategy.NEW]!!,
                excludeContentIds,
                category,
                preferredLanguage,
                excludeCategory
            ).collectList(),
            feedRepository.findRandomContentIds(
                userId,
                strategyLimits[RecommendationStrategy.RANDOM]!!,
                excludeContentIds,
                category,
                preferredLanguage,
                excludeCategory
            ).collectList()
        ).map { tuple ->
            (tuple.t1 + tuple.t2 + tuple.t3 + tuple.t4).shuffled()
        }
    }

    /**
     * FUN 믹싱을 적용한 추천 콘텐츠 조회 (Issue #92)
     *
     * 전체 피드에서 80%는 일반 콘텐츠, 20%는 FUN 카테고리로 구성합니다.
     * **FUN 콘텐츠에도 동일한 4가지 추천 전략을 적용합니다.**
     *
     * ## Best Practice 근거
     * - Netflix/TikTok: 카테고리 믹싱 시에도 각 카테고리 내에서 engagement 기반 랭킹 적용
     * - FUN 콘텐츠도 인기/신규/협업필터링으로 최적의 콘텐츠 제공
     *
     * @param userId 사용자 ID
     * @param limit 총 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @param preferredLanguage 사용자 선호 언어
     * @return 인터리빙된 콘텐츠 ID 목록 (4:1 비율)
     */
    private fun getRecommendedContentIdsWithFunMixing(
        userId: UUID,
        limit: Int,
        excludeContentIds: List<UUID>,
        preferredLanguage: String
    ): Flux<UUID> {
        val regularLimit = (limit * REGULAR_CONTENT_RATIO).toInt().coerceAtLeast(1)
        val funLimit = (limit * FUN_CONTENT_RATIO).toInt().coerceAtLeast(1)

        return Mono.zip(
            // 일반 콘텐츠: FUN 제외, 모든 전략 적용
            fetchContentsByAllStrategies(
                userId,
                regularLimit,
                excludeContentIds,
                preferredLanguage,
                excludeCategory = Category.FUN
            ),
            // FUN 콘텐츠: FUN만, 모든 전략 적용 (협업필터링 + 인기 + 신규 + 랜덤)
            fetchContentsByAllStrategies(
                userId,
                funLimit,
                excludeContentIds,
                preferredLanguage,
                category = Category.FUN
            )
        ).flatMapMany { tuple ->
            val regularIds = tuple.t1
            val funIds = tuple.t2

            val result = interleaveFunContent(regularIds, funIds)
            logger.debug(
                "Feed mixing complete: regularCount={}, funCount={}, totalCount={}",
                regularIds.size, funIds.size, result.size
            )

            Flux.fromIterable(result)
        }
    }

    /**
     * 일반 콘텐츠와 FUN 콘텐츠를 4:1 비율로 인터리빙
     *
     * 5개마다 1개의 FUN 콘텐츠가 포함되도록 배치합니다.
     * 예: [일반, 일반, 일반, 일반, FUN, 일반, 일반, 일반, 일반, FUN, ...]
     *
     * @param regularIds 일반 콘텐츠 ID 목록
     * @param funIds FUN 카테고리 콘텐츠 ID 목록
     * @return 인터리빙된 콘텐츠 ID 목록
     */
    private fun interleaveFunContent(regularIds: List<UUID>, funIds: List<UUID>): List<UUID> {
        if (funIds.isEmpty()) {
            return regularIds
        }

        val result = mutableListOf<UUID>()
        var regularIndex = 0
        var funIndex = 0

        while (regularIndex < regularIds.size || funIndex < funIds.size) {
            // 4개의 일반 콘텐츠 추가
            repeat(INTERLEAVE_REGULAR_COUNT) {
                if (regularIndex < regularIds.size) {
                    result.add(regularIds[regularIndex++])
                }
            }

            // 1개의 FUN 콘텐츠 추가
            if (funIndex < funIds.size) {
                result.add(funIds[funIndex++])
            }
        }

        return result
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
     * 2. 추천 결과가 없으면 (신규 사용자) 인기 콘텐츠로 fallback
     * 3. 추천 결과가 부족하면 인기 콘텐츠로 보충
     *
     * @param userId 사용자 ID
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @param preferredLanguage 사용자 선호 언어
     * @param category 포함할 카테고리 (null이면 전체)
     * @param excludeCategory 제외할 카테고리
     * @return 협업 필터링 추천 콘텐츠 ID 목록
     */
    private fun getCollaborativeContentIds(
        userId: UUID,
        limit: Int,
        excludeContentIds: List<UUID>,
        preferredLanguage: String,
        category: Category? = null,
        excludeCategory: Category? = null
    ): Flux<UUID> {
        // excludeCategory 필터링이 필요한 경우 여유있게 더 조회
        val cfLimit = if (excludeCategory != null) limit * CF_OVERSAMPLE_MULTIPLIER else limit

        return collaborativeFilteringService.getRecommendedContents(userId, cfLimit, preferredLanguage, category)
            .filter { !excludeContentIds.contains(it) }
            .collectList()
            .flatMap { recommendedIds ->
                filterByExcludeCategory(recommendedIds, excludeCategory, limit)
            }
            .flatMapMany { recommendedIds ->
                supplementWithPopularContent(userId, recommendedIds, limit, excludeContentIds, category, preferredLanguage, excludeCategory)
            }
    }

    /**
     * excludeCategory에 해당하는 콘텐츠 필터링
     */
    private fun filterByExcludeCategory(
        contentIds: List<UUID>,
        excludeCategory: Category?,
        limit: Int
    ): Mono<List<UUID>> {
        if (excludeCategory == null || contentIds.isEmpty()) {
            return Mono.just(contentIds.take(limit))
        }

        return feedRepository.findCategoriesByContentIds(contentIds)
            .collectList()
            .map { categories ->
                contentIds.zip(categories)
                    .filter { (_, cat) -> cat != excludeCategory.name }
                    .map { (id, _) -> id }
                    .take(limit)
            }
    }

    /**
     * CF 결과가 부족할 때 인기 콘텐츠로 보충
     */
    private fun supplementWithPopularContent(
        userId: UUID,
        recommendedIds: List<UUID>,
        limit: Int,
        excludeContentIds: List<UUID>,
        category: Category?,
        preferredLanguage: String,
        excludeCategory: Category?
    ): Flux<UUID> {
        return when {
            recommendedIds.isEmpty() -> {
                logger.debug("No CF recommendations for user {}, falling back to popular content", userId)
                feedRepository.findPopularContentIds(userId, limit, excludeContentIds, category, preferredLanguage, excludeCategory)
            }
            recommendedIds.size < limit -> {
                val remaining = limit - recommendedIds.size
                logger.debug("CF recommendations ({}) < limit ({}), supplementing with {} popular contents", recommendedIds.size, limit, remaining)
                Flux.concat(
                    Flux.fromIterable(recommendedIds),
                    feedRepository.findPopularContentIds(userId, remaining, excludeContentIds + recommendedIds, category, preferredLanguage, excludeCategory)
                )
            }
            else -> {
                logger.debug("Returning {} CF recommendations for user {}", recommendedIds.size, userId)
                Flux.fromIterable(recommendedIds.take(limit))
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RecommendationServiceImpl::class.java)

        /** 일반 콘텐츠 비율 (80%) */
        private const val REGULAR_CONTENT_RATIO = 0.8

        /** FUN 콘텐츠 비율 (20%) */
        private const val FUN_CONTENT_RATIO = 0.2

        /** 인터리빙 시 일반 콘텐츠 개수 (4개마다 1개 FUN) */
        private const val INTERLEAVE_REGULAR_COUNT = 4

        /** CF excludeCategory 필터링을 위한 오버샘플링 배수 */
        private const val CF_OVERSAMPLE_MULTIPLIER = 2
    }
}
