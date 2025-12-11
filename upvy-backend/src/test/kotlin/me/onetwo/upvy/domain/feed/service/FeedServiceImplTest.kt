package me.onetwo.upvy.domain.feed.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.feed.dto.CreatorInfoResponse
import me.onetwo.upvy.domain.feed.dto.FeedItemResponse
import me.onetwo.upvy.domain.feed.dto.InteractionInfoResponse
import me.onetwo.upvy.domain.feed.repository.FeedRepository
import me.onetwo.upvy.domain.feed.service.recommendation.RecommendationService
import me.onetwo.upvy.infrastructure.common.dto.CursorPageRequest
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

            every { feedCacheService.getMainFeedBatch(userId, "en", 0) } returns
                Mono.just(cachedBatch)
            every { feedCacheService.getMainFeedBatchSize(any(), any(), any()) } returns Mono.just(250L)
            every { feedRepository.findByContentIds(any(), any()) } returns
                Flux.fromIterable(feedItems)

            // When: 메인 피드 조회
            val result = feedService.getMainFeed(userId, pageRequest, preferredLanguage = "en")

            // Then: 캐시된 배치에서 피드 반환 (limit+1개 조회하여 limit개만 반환)
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(20) // limit만큼만 반환
                    assertThat(response.hasNext).isTrue // 21개 조회되었으므로 hasNext = true
                    assertThat(response.nextCursor).isNotNull
                }
                .verifyComplete()

            // Then: 캐시에서 조회했는지 확인
            verify(exactly = 1) { feedCacheService.getMainFeedBatch(userId, "en", 0) }
            verify(exactly = 1) { feedRepository.findByContentIds(any(), any()) }
            verify(exactly = 0) { recommendationService.getRecommendedContentIds(any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("PHOTO 타입 콘텐츠가 포함된 피드를 조회하면, photoUrls가 함께 반환된다")
        fun getMainFeed_WithPhotoContent_ReturnsPhotoUrls() {
            // Given: PHOTO 타입 콘텐츠를 포함한 배치
            val pageRequest = CursorPageRequest(cursor = null, limit = 20)
            val cachedBatch = List(21) { UUID.randomUUID() }

            // VIDEO와 PHOTO 혼합
            val feedItems = cachedBatch.mapIndexed { index, contentId ->
                if (index % 3 == 0) {
                    // 3개 중 1개는 PHOTO 타입
                    createPhotoFeedItem(contentId, listOf(
                        "https://example.com/photo1.jpg",
                        "https://example.com/photo2.jpg",
                        "https://example.com/photo3.jpg"
                    ))
                } else {
                    // 나머지는 VIDEO 타입
                    createFeedItem(contentId)
                }
            }

            every { feedCacheService.getMainFeedBatch(userId, "en", 0) } returns
                Mono.just(cachedBatch)
            every { feedCacheService.getMainFeedBatchSize(any(), any(), any()) } returns Mono.just(250L)
            every { feedRepository.findByContentIds(any(), any()) } returns
                Flux.fromIterable(feedItems)

            // When: 메인 피드 조회
            val result = feedService.getMainFeed(userId, pageRequest, preferredLanguage = "en")

            // Then: PHOTO 타입 콘텐츠는 photoUrls를 가지고, VIDEO는 null
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(20)

                    // PHOTO 타입 확인 (index 0, 3, 6, 9, 12, 15, 18)
                    val photoContents = response.content.filter { it.contentType == ContentType.PHOTO }
                    assertThat(photoContents).isNotEmpty
                    photoContents.forEach { photoContent ->
                        assertThat(photoContent.photoUrls).isNotNull
                        assertThat(photoContent.photoUrls).hasSize(3)
                    }

                    // VIDEO 타입 확인
                    val videoContents = response.content.filter { it.contentType == ContentType.VIDEO }
                    videoContents.forEach { videoContent ->
                        assertThat(videoContent.photoUrls).isNull()
                    }
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("캐시 miss: 새 배치를 생성하고 Redis에 저장한 후 피드를 반환한다")
        fun getMainFeed_WithoutCachedBatch_GeneratesAndCachesBatch() {
            // Given: 캐시 miss (empty Mono)
            val pageRequest = CursorPageRequest(cursor = null, limit = 20)
            val recentlyViewedIds = List(100) { UUID.randomUUID() }
            val recommendedIds = List(250) { UUID.randomUUID() }
            val feedItems = recommendedIds.take(20).map { createFeedItem(it) }

            every { feedCacheService.getMainFeedBatch(userId, "en", 0) } returns Mono.empty()
            every { feedRepository.findRecentlyViewedContentIds(userId, 100) } returns
                Flux.fromIterable(recentlyViewedIds)
            every {
                recommendationService.getRecommendedContentIds(
                    userId = userId,
                    limit = 250,
                    excludeContentIds = recentlyViewedIds,
                    preferredLanguage = "en",
                    category = null
                )
            } returns Flux.fromIterable(recommendedIds)
            every { feedCacheService.saveMainFeedBatch(userId, "en", 0, recommendedIds) } returns
                Mono.just(true)
            every { feedCacheService.getMainFeedBatchSize(any(), any(), any()) } returns Mono.just(0L)
            every { feedRepository.findByContentIds(any(), any()) } returns
                Flux.fromIterable(feedItems)

            // When: 메인 피드 조회
            val result = feedService.getMainFeed(userId, pageRequest, preferredLanguage = "en")

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
                    excludeContentIds = recentlyViewedIds,
                    preferredLanguage = "en",
                    category = null
                )
            }
            verify(exactly = 1) { feedCacheService.saveMainFeedBatch(userId, "en", 0, recommendedIds) }
        }

        @Test
        @DisplayName("cursor를 offset으로 해석하여 올바른 배치를 조회한다")
        fun getMainFeed_WithCursor_CalculatesCorrectBatch() {
            // Given: cursor = 250 (batch 1)
            val pageRequest = CursorPageRequest(cursor = "250", limit = 20)
            val cachedBatch = List(250) { UUID.randomUUID() }
            val feedItems = cachedBatch.take(20).map { createFeedItem(it) }

            every { feedCacheService.getMainFeedBatch(userId, "en", 1) } returns
                Mono.just(cachedBatch)
            every { feedCacheService.getMainFeedBatchSize(any(), any(), any()) } returns Mono.just(250L)
            every { feedRepository.findByContentIds(any(), any()) } returns
                Flux.fromIterable(feedItems)

            // When: cursor = 250으로 조회
            val result = feedService.getMainFeed(userId, pageRequest, preferredLanguage = "en")

            // Then: batch 1 조회
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(20)
                }
                .verifyComplete()

            verify(exactly = 1) { feedCacheService.getMainFeedBatch(userId, "en", 1) }
        }

        @Test
        @DisplayName("배치 내에서 올바른 범위를 조회한다")
        fun getMainFeed_WithOffsetInBatch_ReturnsCorrectRange() {
            // Given: offset = 10 (batch 0, offset 10)
            val pageRequest = CursorPageRequest(cursor = "10", limit = 20)
            val cachedBatch = List(250) { UUID.randomUUID() }
            val expectedContentIds = cachedBatch.subList(10, 31) // offset 10 ~ 30 (limit+1)
            val feedItems = expectedContentIds.map { createFeedItem(it) }

            every { feedCacheService.getMainFeedBatch(userId, "en", 0) } returns
                Mono.just(cachedBatch)
            every { feedCacheService.getMainFeedBatchSize(any(), any(), any()) } returns Mono.just(250L)
            every { feedRepository.findByContentIds(userId, expectedContentIds) } returns
                Flux.fromIterable(feedItems)

            // When: offset 10에서 조회
            val result = feedService.getMainFeed(userId, pageRequest, preferredLanguage = "en")

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
        @DisplayName("PHOTO 타입 콘텐츠가 포함된 팔로잉 피드를 조회하면, photoUrls가 함께 반환된다")
        fun getFollowingFeed_WithPhotoContent_ReturnsPhotoUrls() {
            // Given: PHOTO 타입을 포함한 팔로잉 피드
            val pageRequest = CursorPageRequest(cursor = null, limit = 20)
            val feedItems = listOf(
                createPhotoFeedItem(UUID.randomUUID(), listOf(
                    "https://example.com/photo1.jpg",
                    "https://example.com/photo2.jpg"
                )),
                createFeedItem(UUID.randomUUID()), // VIDEO
                createPhotoFeedItem(UUID.randomUUID(), listOf(
                    "https://example.com/photo3.jpg"
                ))
            )

            every { feedRepository.findFollowingFeed(userId, null, 21) } returns
                Flux.fromIterable(feedItems)

            // When: 팔로잉 피드 조회
            val result = feedService.getFollowingFeed(userId, pageRequest)

            // Then: PHOTO 타입 콘텐츠는 photoUrls를 가짐
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(3)

                    // 첫 번째 PHOTO (2개 사진)
                    assertThat(response.content[0].contentType).isEqualTo(ContentType.PHOTO)
                    assertThat(response.content[0].photoUrls).isNotNull
                    assertThat(response.content[0].photoUrls).hasSize(2)

                    // VIDEO (photoUrls null)
                    assertThat(response.content[1].contentType).isEqualTo(ContentType.VIDEO)
                    assertThat(response.content[1].photoUrls).isNull()

                    // 두 번째 PHOTO (1개 사진)
                    assertThat(response.content[2].contentType).isEqualTo(ContentType.PHOTO)
                    assertThat(response.content[2].photoUrls).isNotNull
                    assertThat(response.content[2].photoUrls).hasSize(1)
                }
                .verifyComplete()
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

    // TODO: Commented out - findContentIdsByCategory method was deleted during CategoryFeedSortType refactoring
    /*
    @Nested
    @DisplayName("getCategoryFeed - 카테고리별 피드 조회")
    inner class GetCategoryFeed {

        @Test
        @DisplayName("유효한 카테고리로 조회 시, Repository에서 콘텐츠 ID를 가져와 피드를 반환한다")
        fun getCategoryFeed_WithValidCategory_ReturnsContentIds() {
            // Given: 카테고리별 콘텐츠 ID와 피드 데이터
            val pageRequest = CursorPageRequest(cursor = null, limit = 20)
            val contentIds = List(21) { UUID.randomUUID() }
            val feedItems = contentIds.map { createFeedItem(it) } // 21개 모두 생성

            every {
                feedRepository.findContentIdsByCategory(
                    category = Category.PROGRAMMING,
                    sortBy = me.onetwo.upvy.domain.feed.model.CategoryFeedSortType.POPULAR,
                    cursor = null,
                    limit = 21
                )
            } returns Flux.fromIterable(contentIds)

            every { feedRepository.findByContentIds(userId, contentIds) } returns
                Flux.fromIterable(feedItems)

            // When: 카테고리 피드 조회
            val result = feedService.getCategoryFeed(
                userId = userId,
                category = Category.PROGRAMMING,
                sortBy = me.onetwo.upvy.domain.feed.model.CategoryFeedSortType.POPULAR,
                pageRequest = pageRequest
            )

            // Then: Repository 호출 및 피드 반환 (limit+1개 조회하여 limit개만 반환)
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(20) // limit만큼만 반환
                    assertThat(response.hasNext).isTrue // 21개 조회되었으므로 hasNext = true
                    assertThat(response.nextCursor).isNotNull
                }
                .verifyComplete()

            verify(exactly = 1) {
                feedRepository.findContentIdsByCategory(
                    category = Category.PROGRAMMING,
                    sortBy = me.onetwo.upvy.domain.feed.model.CategoryFeedSortType.POPULAR,
                    cursor = null,
                    limit = 21
                )
            }
            verify(exactly = 1) { feedRepository.findByContentIds(userId, contentIds) }
        }

        @Test
        @DisplayName("userId가 null인 경우, 랜덤 UUID를 사용하여 조회한다")
        fun getCategoryFeed_WithNullUserId_UsesRandomUUID() {
            // Given: userId가 null인 비인증 사용자
            val pageRequest = CursorPageRequest(cursor = null, limit = 20)
            val contentIds = List(10) { UUID.randomUUID() }
            val feedItems = contentIds.map { createFeedItem(it) }

            every {
                feedRepository.findContentIdsByCategory(
                    category = Category.LANGUAGE,
                    sortBy = me.onetwo.upvy.domain.feed.model.CategoryFeedSortType.RECENT,
                    cursor = null,
                    limit = 21
                )
            } returns Flux.fromIterable(contentIds)

            every { feedRepository.findByContentIds(any(), contentIds) } returns
                Flux.fromIterable(feedItems)

            // When: userId = null로 카테고리 피드 조회
            val result = feedService.getCategoryFeed(
                userId = null,
                category = Category.LANGUAGE,
                sortBy = me.onetwo.upvy.domain.feed.model.CategoryFeedSortType.RECENT,
                pageRequest = pageRequest
            )

            // Then: 랜덤 UUID로 조회 (비인증 사용자는 좋아요/저장 상태가 false)
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).hasSize(10)
                    assertThat(response.hasNext).isFalse
                }
                .verifyComplete()

            verify(exactly = 1) {
                feedRepository.findContentIdsByCategory(
                    category = Category.LANGUAGE,
                    sortBy = me.onetwo.upvy.domain.feed.model.CategoryFeedSortType.RECENT,
                    cursor = null,
                    limit = 21
                )
            }
            verify(exactly = 1) { feedRepository.findByContentIds(any(), contentIds) }
        }
    }
    */

    /**
     * 테스트용 FeedItemResponse 생성 (VIDEO 타입)
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
                followerCount = 100,
                isFollowing = false
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

    /**
     * 테스트용 FeedItemResponse 생성 (PHOTO 타입)
     */
    private fun createPhotoFeedItem(contentId: UUID, photoUrls: List<String>): FeedItemResponse {
        return FeedItemResponse(
            contentId = contentId,
            contentType = ContentType.PHOTO,
            url = photoUrls.firstOrNull() ?: "https://example.com/photo.jpg", // 대표 이미지
            photoUrls = photoUrls,
            thumbnailUrl = "https://example.com/thumbnail.jpg",
            duration = null, // PHOTO는 duration 없음
            width = 1080,
            height = 1080,
            title = "테스트 사진 제목",
            description = "테스트 사진 설명",
            category = Category.ART,
            tags = listOf("photo", "test"),
            creator = CreatorInfoResponse(
                userId = UUID.randomUUID(),
                nickname = "사진작가",
                profileImageUrl = "https://example.com/profile.jpg",
                followerCount = 200,
                isFollowing = false
            ),
            interactions = InteractionInfoResponse(
                likeCount = 150,
                commentCount = 75,
                saveCount = 50,
                shareCount = 30,
                viewCount = 2000,
                isLiked = false,
                isSaved = false
            ),
            subtitles = emptyList()
        )
    }
}
