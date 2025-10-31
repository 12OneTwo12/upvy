package me.onetwo.growsnap.domain.feed.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.feed.dto.CreatorInfoResponse
import me.onetwo.growsnap.domain.feed.dto.FeedItemResponse
import me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse
import me.onetwo.growsnap.domain.feed.repository.FeedRepository
import me.onetwo.growsnap.domain.feed.service.recommendation.RecommendationService
import me.onetwo.growsnap.infrastructure.common.dto.CursorPageRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

/**
 * 피드 서비스 구현체 테스트
 *
 * TikTok/Instagram Reels 방식의 피드 캐싱 로직을 검증합니다.
 * - Redis 캐시 hit/miss
 * - 배치 생성 및 저장
 * - Prefetch 로직
 * - 팔로잉 피드
 */
@ExtendWith(MockKExtension::class)
@DisplayName("피드 Service 테스트")
class FeedServiceImplTest {

    private lateinit var feedRepository: FeedRepository
    private lateinit var recommendationService: RecommendationService
    private lateinit var feedCacheService: FeedCacheService
    private lateinit var feedService: FeedServiceImpl

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        feedRepository = mockk(relaxed = true)
        recommendationService = mockk(relaxed = true)
        feedCacheService = mockk(relaxed = true)
        feedService = FeedServiceImpl(feedRepository, recommendationService, feedCacheService)
    }

    @Nested
    @DisplayName("getMainFeed - 메인 피드 조회")
    inner class GetMainFeed {

        @Test
        @DisplayName("캐시 hit: Redis에서 배치를 가져와 피드를 반환한다")
        fun getMainFeed_WithCachedBatch_ReturnsFeedFromCache() {
            // Given: Redis에 캐싱된 배치
            val pageRequest = CursorPageRequest(cursor = null, limit = 20)
            val cachedBatch = List(250) { UUID.randomUUID() }
            // limit+1개를 조회하므로 21개 제공
            val feedItems = cachedBatch.take(21).map { createFeedItem(it) }

            every { feedCacheService.getRecommendationBatch(userId, 0) } returns
                Mono.just(cachedBatch)
            every { feedCacheService.getBatchSize(any(), any()) } returns Mono.just(250L)
            every { feedRepository.findByContentIds(any(), any()) } returns
                Flux.fromIterable(feedItems)

            // When: 메인 피드 조회
            val result = feedService.getMainFeed(userId, pageRequest)

            // Then: 캐시된 배치에서 피드 반환 (limit+1개 조회하여 limit개만 반환)
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(20) // limit만큼만 반환
                    assertThat(response.hasNext).isTrue // 21개 조회되었으므로 hasNext = true
                    assertThat(response.nextCursor).isNotNull
                }
                .verifyComplete()

            // Then: 캐시에서 조회했는지 확인
            verify(exactly = 1) { feedCacheService.getRecommendationBatch(userId, 0) }
            verify(exactly = 1) { feedRepository.findByContentIds(any(), any()) }
            verify(exactly = 0) { recommendationService.getRecommendedContentIds(any(), any(), any()) }
        }

        @Test
        @DisplayName("캐시 miss: 새 배치를 생성하고 Redis에 저장한 후 피드를 반환한다")
        fun getMainFeed_WithoutCachedBatch_GeneratesAndCachesBatch() {
            // Given: 캐시 miss (empty Mono)
            val pageRequest = CursorPageRequest(cursor = null, limit = 20)
            val recentlyViewedIds = List(100) { UUID.randomUUID() }
            val recommendedIds = List(250) { UUID.randomUUID() }
            val feedItems = recommendedIds.take(20).map { createFeedItem(it) }

            every { feedCacheService.getRecommendationBatch(userId, 0) } returns Mono.empty()
            every { feedRepository.findRecentlyViewedContentIds(userId, 100) } returns
                Flux.fromIterable(recentlyViewedIds)
            every {
                recommendationService.getRecommendedContentIds(
                    userId = userId,
                    limit = 250,
                    excludeContentIds = recentlyViewedIds
                )
            } returns Flux.fromIterable(recommendedIds)
            every { feedCacheService.saveRecommendationBatch(userId, 0, recommendedIds) } returns
                Mono.just(true)
            every { feedCacheService.getBatchSize(any(), any()) } returns Mono.just(0L)
            every { feedRepository.findByContentIds(any(), any()) } returns
                Flux.fromIterable(feedItems)

            // When: 메인 피드 조회
            val result = feedService.getMainFeed(userId, pageRequest)

            // Then: 새 배치 생성 후 피드 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(20)
                }
                .verifyComplete()

            // Then: 추천 알고리즘 실행 및 캐시 저장 확인
            verify(exactly = 1) { feedRepository.findRecentlyViewedContentIds(userId, 100) }
            verify(exactly = 1) {
                recommendationService.getRecommendedContentIds(
                    userId = userId,
                    limit = 250,
                    excludeContentIds = recentlyViewedIds
                )
            }
            verify(exactly = 1) { feedCacheService.saveRecommendationBatch(userId, 0, recommendedIds) }
        }

        @Test
        @DisplayName("cursor를 offset으로 해석하여 올바른 배치를 조회한다")
        fun getMainFeed_WithCursor_CalculatesCorrectBatch() {
            // Given: cursor = 250 (batch 1)
            val pageRequest = CursorPageRequest(cursor = "250", limit = 20)
            val cachedBatch = List(250) { UUID.randomUUID() }
            val feedItems = cachedBatch.take(20).map { createFeedItem(it) }

            every { feedCacheService.getRecommendationBatch(userId, 1) } returns
                Mono.just(cachedBatch)
            every { feedCacheService.getBatchSize(any(), any()) } returns Mono.just(250L)
            every { feedRepository.findByContentIds(any(), any()) } returns
                Flux.fromIterable(feedItems)

            // When: cursor = 250으로 조회
            val result = feedService.getMainFeed(userId, pageRequest)

            // Then: batch 1 조회
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(20)
                }
                .verifyComplete()

            verify(exactly = 1) { feedCacheService.getRecommendationBatch(userId, 1) }
        }

        @Test
        @DisplayName("배치 내에서 올바른 범위를 조회한다")
        fun getMainFeed_WithOffsetInBatch_ReturnsCorrectRange() {
            // Given: offset = 10 (batch 0, offset 10)
            val pageRequest = CursorPageRequest(cursor = "10", limit = 20)
            val cachedBatch = List(250) { UUID.randomUUID() }
            val expectedContentIds = cachedBatch.subList(10, 31) // offset 10 ~ 30 (limit+1)
            val feedItems = expectedContentIds.map { createFeedItem(it) }

            every { feedCacheService.getRecommendationBatch(userId, 0) } returns
                Mono.just(cachedBatch)
            every { feedCacheService.getBatchSize(any(), any()) } returns Mono.just(250L)
            every { feedRepository.findByContentIds(userId, expectedContentIds) } returns
                Flux.fromIterable(feedItems)

            // When: offset 10에서 조회
            val result = feedService.getMainFeed(userId, pageRequest)

            // Then: 올바른 범위 조회
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(20)
                }
                .verifyComplete()

            verify(exactly = 1) { feedRepository.findByContentIds(userId, expectedContentIds) }
        }
    }

    @Nested
    @DisplayName("getFollowingFeed - 팔로잉 피드 조회")
    inner class GetFollowingFeed {

        @Test
        @DisplayName("팔로잉 피드를 조회하여 반환한다")
        fun getFollowingFeed_ReturnsFollowingFeed() {
            // Given: 팔로잉 피드 데이터
            val pageRequest = CursorPageRequest(cursor = null, limit = 20)
            val feedItems = List(21) { createFeedItem(UUID.randomUUID()) }

            every { feedRepository.findFollowingFeed(userId, null, 21) } returns
                Flux.fromIterable(feedItems)

            // When: 팔로잉 피드 조회
            val result = feedService.getFollowingFeed(userId, pageRequest)

            // Then: 피드 반환 (limit+1개 조회하여 hasNext 판단)
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(20) // limit만큼만 반환
                    assertThat(response.hasNext).isTrue
                    assertThat(response.nextCursor).isNotNull // 21개 조회되었으므로 hasNext = true
                }
                .verifyComplete()

            verify(exactly = 1) { feedRepository.findFollowingFeed(userId, null, 21) }
        }

        @Test
        @DisplayName("cursor를 UUID로 변환하여 조회한다")
        fun getFollowingFeed_WithCursor_ConvertsToUUID() {
            // Given: cursor = UUID
            val cursorUuid = UUID.randomUUID()
            val pageRequest = CursorPageRequest(cursor = cursorUuid.toString(), limit = 20)
            val feedItems = List(10) { createFeedItem(UUID.randomUUID()) }

            every { feedRepository.findFollowingFeed(userId, cursorUuid, 21) } returns
                Flux.fromIterable(feedItems)

            // When: cursor와 함께 조회
            val result = feedService.getFollowingFeed(userId, pageRequest)

            // Then: cursor UUID로 변환하여 조회
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(10)
                    assertThat(response.hasNext).isFalse
                }
                .verifyComplete()

            verify(exactly = 1) { feedRepository.findFollowingFeed(userId, cursorUuid, 21) }
        }
    }

    /**
     * 테스트용 FeedItemResponse 생성
     */
    private fun createFeedItem(contentId: UUID): FeedItemResponse {
        return FeedItemResponse(
            contentId = contentId,
            contentType = ContentType.VIDEO,
            url = "https://example.com/video.mp4",
            photoUrls = null,
            thumbnailUrl = "https://example.com/thumbnail.jpg",
            duration = 30,
            width = 1080,
            height = 1920,
            title = "테스트 제목",
            description = "테스트 설명",
            category = Category.PROGRAMMING,
            tags = listOf("kotlin", "spring"),
            creator = CreatorInfoResponse(
                userId = UUID.randomUUID(),
                nickname = "테스터",
                profileImageUrl = "https://example.com/profile.jpg",
                followerCount = 100
            ),
            interactions = InteractionInfoResponse(
                likeCount = 100,
                commentCount = 50,
                saveCount = 30,
                shareCount = 20,
                viewCount = 1000,
                isLiked = false,
                isSaved = false
            ),
            subtitles = emptyList()
        )
    }
}
