package me.onetwo.growsnap.domain.feed.service.recommendation

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.feed.repository.FeedRepository
import me.onetwo.growsnap.domain.feed.service.collaborative.CollaborativeFilteringService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.UUID

/**
 * RecommendationServiceImpl 단위 테스트
 *
 * 추천 서비스의 핵심 로직을 검증합니다:
 * - 4가지 추천 전략의 병렬 실행 (Mono.zip)
 * - 협업 필터링 fallback 및 보충 로직
 * - excludeContentIds 필터링
 * - 결과 무작위 섞기
 */
@ExtendWith(MockKExtension::class)
@DisplayName("추천 서비스 단위 테스트")
class RecommendationServiceImplTest {

    private lateinit var feedRepository: FeedRepository
    private lateinit var collaborativeFilteringService: CollaborativeFilteringService
    private lateinit var recommendationService: RecommendationServiceImpl

    private val testUserId = UUID.randomUUID()
    private val contentId1 = UUID.randomUUID()
    private val contentId2 = UUID.randomUUID()
    private val contentId3 = UUID.randomUUID()
    private val contentId4 = UUID.randomUUID()
    private val contentId5 = UUID.randomUUID()
    private val contentId6 = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        feedRepository = mockk()
        collaborativeFilteringService = mockk()
        recommendationService = RecommendationServiceImpl(
            feedRepository,
            collaborativeFilteringService
        )
    }

    @Nested
    @DisplayName("getRecommendedContentIds - 추천 콘텐츠 ID 조회")
    inner class GetRecommendedContentIds {

        @Test
        @DisplayName("4가지 전략을 병렬로 실행하여 결과를 반환한다")
        fun getRecommendedContentIds_ExecutesAllStrategiesInParallel() {
            // Given: 각 전략별 충분한 결과 준비 (CF fallback 방지)
            val cfIds = listOf(contentId1, contentId2, contentId3, contentId4)  // CF 40% = 4개
            val popularIds = listOf(contentId5)
            val newIds = listOf(contentId6)
            val randomIds = listOf(UUID.randomUUID())

            // CF가 충분한 결과를 반환하므로 fallback 없음
            every { collaborativeFilteringService.getRecommendedContents(testUserId, 4, "en", Category.PROGRAMMING, null) } returns Flux.fromIterable(cfIds)
            every { feedRepository.findPopularContentIds(testUserId, 3, emptyList(), Category.PROGRAMMING, "en", any()) } returns Flux.fromIterable(popularIds)
            every { feedRepository.findNewContentIds(testUserId, 1, emptyList(), Category.PROGRAMMING, "en", any()) } returns Flux.fromIterable(newIds)
            every { feedRepository.findRandomContentIds(testUserId, 2, emptyList(), Category.PROGRAMMING, "en", any()) } returns Flux.fromIterable(randomIds)

            // When: 추천 콘텐츠 조회 (limit=10)
            val result = recommendationService.getRecommendedContentIds(
                testUserId,
                limit = 10,
                excludeContentIds = emptyList(),
                preferredLanguage = "en",
                category = Category.PROGRAMMING
            )

            // Then: 모든 전략의 결과가 반환됨 (최대 7개: CF 4 + Popular 1 + New 1 + Random 1)
            StepVerifier.create(result.collectList())
                .assertNext { ids ->
                    assertTrue(ids.size >= 4)  // 최소한 CF 결과는 포함
                    assertTrue(ids.contains(contentId1))
                    assertTrue(ids.contains(contentId2))
                    assertTrue(ids.contains(contentId3))
                    assertTrue(ids.contains(contentId4))
                }
                .verifyComplete()

            // Then: 모든 전략이 호출됨
            verify(exactly = 1) { collaborativeFilteringService.getRecommendedContents(testUserId, 4, "en", Category.PROGRAMMING, null) }
            verify(atLeast = 1) { feedRepository.findPopularContentIds(any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { feedRepository.findNewContentIds(any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { feedRepository.findRandomContentIds(any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("excludeContentIds에 포함된 콘텐츠는 제외된다")
        fun getRecommendedContentIds_ExcludesSpecifiedContentIds() {
            // Given: 일부 콘텐츠가 exclude 목록에 포함
            // CF가 충분한 결과 반환하되, exclude 후에도 충분하도록 5개 준비
            val cfIds = listOf(contentId1, contentId2, contentId3, contentId4, UUID.randomUUID())
            val popularIds = listOf(contentId5)
            val excludedIds = listOf(contentId1)

            // CF 결과에서 contentId1이 필터링되어야 함 (5개 -> 4개 남음, limit 4개 충분)
            every { collaborativeFilteringService.getRecommendedContents(testUserId, 4, "en", Category.PROGRAMMING, null) } returns Flux.fromIterable(cfIds)
            every { feedRepository.findPopularContentIds(testUserId, 3, excludedIds, Category.PROGRAMMING, "en", any()) } returns Flux.fromIterable(popularIds)
            // CF fill을 위한 추가 mock (CF가 limit보다 적으면 fill 시도)
            every { feedRepository.findPopularContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findNewContentIds(testUserId, 1, excludedIds, Category.PROGRAMMING, "en", any()) } returns Flux.empty()
            every { feedRepository.findRandomContentIds(testUserId, 2, excludedIds, Category.PROGRAMMING, "en", any()) } returns Flux.empty()

            // When: excludeContentIds 지정하여 조회
            val result = recommendationService.getRecommendedContentIds(
                testUserId,
                limit = 10,
                excludeContentIds = excludedIds,
                preferredLanguage = "en",
                category = Category.PROGRAMMING
            )

            // Then: excluded 콘텐츠는 결과에 포함되지 않음
            StepVerifier.create(result.collectList())
                .assertNext { ids ->
                    assertTrue(ids.isNotEmpty())
                    // contentId1은 필터링되어 제외됨
                    assertTrue(!ids.contains(contentId1), "contentId1 should be excluded")
                    // 다른 CF 결과는 포함됨
                    assertTrue(ids.contains(contentId2), "contentId2 should be included")
                    assertTrue(ids.contains(contentId3), "contentId3 should be included")
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("limit에 맞게 각 전략별 비율로 콘텐츠를 조회한다")
        fun getRecommendedContentIds_RespectsStrategyRatios() {
            // Given: limit=100
            every { collaborativeFilteringService.getRecommendedContents(testUserId, any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findPopularContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findNewContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findRandomContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()

            // When: limit=100으로 조회
            recommendationService.getRecommendedContentIds(
                testUserId,
                limit = 100,
                excludeContentIds = emptyList(),
                preferredLanguage = "en",
                category = Category.PROGRAMMING
            ).collectList().block()

            // Then: 각 전략별 비율로 조회됨
            // COLLABORATIVE: 40% = 40개
            // POPULAR: 30% = 30개
            // NEW: 10% = 10개
            // RANDOM: 20% = 20개
            verify(exactly = 1) { collaborativeFilteringService.getRecommendedContents(testUserId, 40, "en", Category.PROGRAMMING, null) }
            verify(exactly = 1) { feedRepository.findPopularContentIds(testUserId, 30, emptyList(), Category.PROGRAMMING, "en", any()) }
            verify(exactly = 1) { feedRepository.findNewContentIds(testUserId, 10, emptyList(), Category.PROGRAMMING, "en", any()) }
            verify(exactly = 1) { feedRepository.findRandomContentIds(testUserId, 20, emptyList(), Category.PROGRAMMING, "en", any()) }
        }

        @Test
        @DisplayName("모든 전략이 빈 결과를 반환하면 빈 목록을 반환한다")
        fun getRecommendedContentIds_WhenAllStrategiesReturnEmpty_ReturnsEmptyList() {
            // Given: 모든 전략이 빈 결과 반환
            every { collaborativeFilteringService.getRecommendedContents(testUserId, any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findPopularContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findNewContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findRandomContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()

            // When: 추천 콘텐츠 조회
            val result = recommendationService.getRecommendedContentIds(
                testUserId,
                limit = 10,
                excludeContentIds = emptyList(),
                preferredLanguage = "en",
                category = Category.PROGRAMMING
            )

            // Then: 빈 목록 반환
            StepVerifier.create(result.collectList())
                .assertNext { ids ->
                    assertEquals(0, ids.size)
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Collaborative Filtering 전략 테스트")
    inner class CollaborativeFilteringStrategy {

        @Test
        @DisplayName("CF 추천 결과가 충분하면 CF 결과만 반환한다")
        fun collaborative_WhenEnoughResults_ReturnsOnlyCFResults() {
            // Given: CF가 충분한 결과 반환
            val cfIds = listOf(contentId1, contentId2, contentId3, contentId4)
            every { collaborativeFilteringService.getRecommendedContents(testUserId, 4, "en", Category.PROGRAMMING, null) } returns Flux.fromIterable(cfIds)

            // Mock other strategies (not called in this scenario)
            every { feedRepository.findPopularContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findNewContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findRandomContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()

            // When: limit=10 (CF는 40% = 4개)
            val result = recommendationService.getRecommendedContentIds(
                testUserId,
                limit = 10,
                excludeContentIds = emptyList(),
                preferredLanguage = "en",
                category = Category.PROGRAMMING
            )

            // Then: CF 결과 4개가 포함됨
            StepVerifier.create(result.collectList())
                .assertNext { ids ->
                    assertTrue(ids.contains(contentId1))
                    assertTrue(ids.contains(contentId2))
                    assertTrue(ids.contains(contentId3))
                    assertTrue(ids.contains(contentId4))
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("CF 추천 결과가 없으면 인기 콘텐츠로 fallback한다")
        fun collaborative_WhenNoResults_FallbackToPopular() {
            // Given: CF가 빈 결과 반환
            every { collaborativeFilteringService.getRecommendedContents(testUserId, 4, "en", Category.PROGRAMMING, null) } returns Flux.empty()

            // Popular content as fallback
            val popularIds = listOf(contentId3, contentId4, contentId5, contentId6)
            every { feedRepository.findPopularContentIds(testUserId, 4, emptyList(), Category.PROGRAMMING, "en", any()) } returns Flux.fromIterable(popularIds)
            every { feedRepository.findPopularContentIds(testUserId, 3, emptyList(), Category.PROGRAMMING, "en", any()) } returns Flux.fromIterable(popularIds.take(3))
            every { feedRepository.findNewContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findRandomContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()

            // When: 추천 콘텐츠 조회
            val result = recommendationService.getRecommendedContentIds(
                testUserId,
                limit = 10,
                excludeContentIds = emptyList(),
                preferredLanguage = "en",
                category = Category.PROGRAMMING
            )

            // Then: 인기 콘텐츠가 fallback으로 반환됨
            StepVerifier.create(result.collectList())
                .assertNext { ids ->
                    assertTrue(ids.isNotEmpty())
                    // CF fallback으로 popular content가 포함됨
                    assertTrue(ids.any { it in popularIds })
                }
                .verifyComplete()

            // CF가 2번 호출됨 (초기 CF + fallback에서의 popular 조회)
            verify(atLeast = 1) { feedRepository.findPopularContentIds(any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("CF 추천 결과가 부족하면 인기 콘텐츠로 보충한다")
        fun collaborative_WhenPartialResults_FillWithPopular() {
            // Given: CF가 부족한 결과 반환 (limit의 절반만)
            val cfIds = listOf(contentId1, contentId2)  // 2개만
            every { collaborativeFilteringService.getRecommendedContents(testUserId, 4, "en", Category.PROGRAMMING, null) } returns Flux.fromIterable(cfIds)

            // Popular content to fill
            val popularIds = listOf(contentId3, contentId4)
            every { feedRepository.findPopularContentIds(testUserId, 2, cfIds, Category.PROGRAMMING, "en", any()) } returns Flux.fromIterable(popularIds)
            every { feedRepository.findPopularContentIds(testUserId, 3, emptyList(), Category.PROGRAMMING, "en", any()) } returns Flux.fromIterable(listOf(contentId5))
            every { feedRepository.findNewContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findRandomContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()

            // When: 추천 콘텐츠 조회
            val result = recommendationService.getRecommendedContentIds(
                testUserId,
                limit = 10,
                excludeContentIds = emptyList(),
                preferredLanguage = "en",
                category = Category.PROGRAMMING
            )

            // Then: CF + Popular 결과가 합쳐져 반환됨
            StepVerifier.create(result.collectList())
                .assertNext { ids ->
                    // CF 결과가 포함됨
                    assertTrue(ids.contains(contentId1))
                    assertTrue(ids.contains(contentId2))
                }
                .verifyComplete()

            verify(exactly = 1) { collaborativeFilteringService.getRecommendedContents(testUserId, 4, "en", Category.PROGRAMMING, null) }
        }
    }

    @Nested
    @DisplayName("전략별 위임 테스트")
    inner class StrategyDelegation {

        @Test
        @DisplayName("인기 콘텐츠 조회는 FeedRepository에 위임한다")
        fun popular_DelegatesToFeedRepository() {
            // Given: CF가 충분한 결과 반환 (fallback 방지)
            val cfIds = listOf(contentId1, contentId2, contentId3, contentId4)
            val popularIds = listOf(contentId5)
            val excludedIds = listOf(contentId6)

            every { collaborativeFilteringService.getRecommendedContents(testUserId, 4, "en", Category.PROGRAMMING, null) } returns Flux.fromIterable(cfIds)
            every { feedRepository.findPopularContentIds(testUserId, 3, excludedIds, Category.PROGRAMMING, "en", any()) } returns Flux.fromIterable(popularIds)
            every { feedRepository.findNewContentIds(testUserId, 1, excludedIds, Category.PROGRAMMING, "en", any()) } returns Flux.empty()
            every { feedRepository.findRandomContentIds(testUserId, 2, excludedIds, Category.PROGRAMMING, "en", any()) } returns Flux.empty()

            // When
            recommendationService.getRecommendedContentIds(
                testUserId,
                limit = 10,
                excludeContentIds = excludedIds,
                preferredLanguage = "en",
                category = Category.PROGRAMMING
            ).collectList().block()

            // Then: Popular 전략이 호출됨 (excludedIds 전달)
            verify(exactly = 1) { feedRepository.findPopularContentIds(testUserId, 3, excludedIds, Category.PROGRAMMING, "en", any()) }
        }

        @Test
        @DisplayName("신규 콘텐츠 조회는 FeedRepository에 위임한다")
        fun new_DelegatesToFeedRepository() {
            // Given: CF가 충분한 결과 반환 (fallback 방지)
            val cfIds = listOf(contentId1, contentId2, contentId3, contentId4)
            val newIds = listOf(contentId5)
            val excludedIds = listOf(contentId6)

            every { collaborativeFilteringService.getRecommendedContents(testUserId, 4, "en", Category.PROGRAMMING, null) } returns Flux.fromIterable(cfIds)
            every { feedRepository.findPopularContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findNewContentIds(testUserId, 1, excludedIds, Category.PROGRAMMING, "en", any()) } returns Flux.fromIterable(newIds)
            every { feedRepository.findRandomContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()

            // When
            recommendationService.getRecommendedContentIds(
                testUserId,
                limit = 10,
                excludeContentIds = excludedIds,
                preferredLanguage = "en",
                category = Category.PROGRAMMING
            ).collectList().block()

            // Then: New 전략이 호출됨 (excludedIds 전달)
            verify(exactly = 1) { feedRepository.findNewContentIds(testUserId, 1, excludedIds, Category.PROGRAMMING, "en", any()) }
        }

        @Test
        @DisplayName("랜덤 콘텐츠 조회는 FeedRepository에 위임한다")
        fun random_DelegatesToFeedRepository() {
            // Given: CF가 충분한 결과 반환 (fallback 방지)
            val cfIds = listOf(contentId1, contentId2, contentId3, contentId4)
            val randomIds = listOf(contentId5)
            val excludedIds = listOf(contentId6)

            every { collaborativeFilteringService.getRecommendedContents(testUserId, 4, "en", Category.PROGRAMMING, null) } returns Flux.fromIterable(cfIds)
            every { feedRepository.findPopularContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findNewContentIds(any(), any(), any(), any(), any(), any()) } returns Flux.empty()
            every { feedRepository.findRandomContentIds(testUserId, 2, excludedIds, Category.PROGRAMMING, "en", any()) } returns Flux.fromIterable(randomIds)

            // When
            recommendationService.getRecommendedContentIds(
                testUserId,
                limit = 10,
                excludeContentIds = excludedIds,
                preferredLanguage = "en",
                category = Category.PROGRAMMING
            ).collectList().block()

            // Then: Random 전략이 호출됨 (excludedIds 전달)
            verify(exactly = 1) { feedRepository.findRandomContentIds(testUserId, 2, excludedIds, Category.PROGRAMMING, "en", any()) }
        }
    }
}
