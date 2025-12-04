package me.onetwo.growsnap.domain.feed.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.Content
import me.onetwo.growsnap.domain.content.model.ContentInteraction
import me.onetwo.growsnap.domain.content.model.ContentMetadata
import me.onetwo.growsnap.domain.content.model.ContentPhoto
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.content.repository.ContentPhotoRepository
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import me.onetwo.growsnap.domain.user.model.Follow
import me.onetwo.growsnap.domain.user.repository.FollowRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.infrastructure.config.AbstractIntegrationTest
import me.onetwo.growsnap.util.createContent
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("피드 Controller 통합 테스트")
class FeedControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Autowired
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @Autowired
    private lateinit var contentPhotoRepository: ContentPhotoRepository

    @Autowired
    private lateinit var followRepository: FollowRepository

    @Autowired
    private lateinit var redisTemplate: ReactiveRedisTemplate<String, String>

    @Nested
    @DisplayName("GET /api/v1/feed - 메인 피드 조회")
    inner class GetMainFeed {

        @Test
        @DisplayName("유효한 요청으로 조회 시, 200 OK와 피드 목록을 반환한다")
        fun getMainFeed_WithValidRequest_ReturnsOkAndFeedItems() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository = contentRepository,
                creatorId = user.id!!
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path(ApiPaths.API_V1_FEED)
                        .queryParam("limit", 10)
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.hasNext").isBoolean
                .jsonPath("$.count").isNumber
        }

        @Test
        @DisplayName("커서와 함께 요청 시, 200 OK와 다음 페이지를 반환한다")
        fun getMainFeed_WithCursor_ReturnsOkAndNextPage() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository = contentRepository,
                creatorId = user.id!!
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path(ApiPaths.API_V1_FEED)
                        .queryParam("cursor", content.id!!.toString())
                        .queryParam("limit", 10)
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        @DisplayName("인증되지 않은 요청 시, 401 Unauthorized를 반환한다")
        fun getMainFeed_WithoutAuth_ReturnsUnauthorized() {
            // When & Then: 인증 없이 API 호출
            webTestClient
                .get()
                .uri(ApiPaths.API_V1_FEED)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        @DisplayName("PHOTO 타입 콘텐츠 조회 시, photoUrls가 응답에 포함된다")
        fun getMainFeed_WithPhotoContent_ReturnsPhotoUrls() {
            // Given: 사용자와 PHOTO 타입 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            // PHOTO 타입 콘텐츠 생성
            val photoContent = Content(
                id = java.util.UUID.randomUUID(),
                creatorId = user.id!!,
                contentType = ContentType.PHOTO,
                url = "https://example.com/photo1.jpg",
                thumbnailUrl = "https://example.com/photo-thumbnail.jpg",
                duration = null,
                width = 1080,
                height = 1080,
                status = ContentStatus.PUBLISHED
            )
            val savedPhotoContent = contentRepository.save(photoContent).block()!!

            // ContentMetadata 저장
            contentRepository.saveMetadata(
                ContentMetadata(
                    contentId = savedPhotoContent.id!!,
                    title = "Test Photo Content",
                    description = "Test Photo Description",
                    category = Category.ART,
                    tags = listOf("test", "photo"),
                    language = "ko"
                )
            ).block()

            // ContentPhoto 저장 (여러 사진)
            val photoUrls = listOf(
                "https://example.com/photo1.jpg",
                "https://example.com/photo2.jpg",
                "https://example.com/photo3.jpg"
            )
            reactor.core.publisher.Flux.fromIterable(photoUrls.withIndex())
                .flatMap { (index, photoUrl) ->
                    contentPhotoRepository.save(
                        ContentPhoto(
                            contentId = savedPhotoContent.id!!,
                            photoUrl = photoUrl,
                            displayOrder = index,
                            width = 1080,
                            height = 1080
                        )
                    )
                }
                .then()
                .block()

            // ContentInteraction 초기화
            contentInteractionRepository.create(
                ContentInteraction(
                    contentId = savedPhotoContent.id!!,
                    likeCount = 0,
                    commentCount = 0,
                    shareCount = 0,
                    saveCount = 0,
                    viewCount = 0
                )
            ).block()

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path(ApiPaths.API_V1_FEED)
                        .queryParam("limit", 10)
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.content[0].contentType").isEqualTo("PHOTO")
                .jsonPath("$.content[0].photoUrls").isArray
                .jsonPath("$.content[0].photoUrls.length()").isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/feed/following - 팔로잉 피드 조회")
    inner class GetFollowingFeed {

        @Test
        @DisplayName("유효한 요청으로 조회 시, 200 OK와 팔로잉 피드 목록을 반환한다")
        fun getFollowingFeed_WithValidRequest_ReturnsOkAndFeedItems() {
            // Given: 사용자 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path("${ApiPaths.API_V1_FEED}/following")
                        .queryParam("limit", 10)
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.hasNext").isBoolean
        }

        @Test
        @DisplayName("인증되지 않은 요청 시, 401 Unauthorized를 반환한다")
        fun getFollowingFeed_WithoutAuth_ReturnsUnauthorized() {
            // When & Then: 인증 없이 API 호출
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1_FEED}/following")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        @DisplayName("팔로잉 피드에서 PHOTO 타입 콘텐츠 조회 시, photoUrls가 응답에 포함된다")
        fun getFollowingFeed_WithPhotoContent_ReturnsPhotoUrls() {
            // Given: 팔로워와 팔로잉 사용자 생성
            val (follower, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "follower@example.com",
                providerId = "google-follower"
            )

            val (following, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "following@example.com",
                providerId = "google-following"
            )

            // 팔로우 관계 생성
            followRepository.save(
                Follow(
                    followerId = follower.id!!,
                    followingId = following.id!!
                )
            ).block()

            // PHOTO 타입 콘텐츠 생성 (팔로잉한 사용자가 작성)
            val photoContent = Content(
                id = java.util.UUID.randomUUID(),
                creatorId = following.id!!,
                contentType = ContentType.PHOTO,
                url = "https://example.com/photo1.jpg",
                thumbnailUrl = "https://example.com/photo-thumbnail.jpg",
                duration = null,
                width = 1080,
                height = 1350,
                status = ContentStatus.PUBLISHED
            )
            val savedPhotoContent = contentRepository.save(photoContent).block()!!

            // ContentMetadata 저장
            contentRepository.saveMetadata(
                ContentMetadata(
                    contentId = savedPhotoContent.id!!,
                    title = "Test Photo Content",
                    description = "Test Photo Description",
                    category = Category.ART,
                    tags = listOf("test", "photo", "following"),
                    language = "ko"
                )
            ).block()

            // ContentPhoto 저장 (여러 사진)
            val photoUrls = listOf(
                "https://example.com/photo1.jpg",
                "https://example.com/photo2.jpg",
                "https://example.com/photo3.jpg",
                "https://example.com/photo4.jpg"
            )
            reactor.core.publisher.Flux.fromIterable(photoUrls.withIndex())
                .flatMap { (index, photoUrl) ->
                    contentPhotoRepository.save(
                        ContentPhoto(
                            contentId = savedPhotoContent.id!!,
                            photoUrl = photoUrl,
                            displayOrder = index,
                            width = 1080,
                            height = 1350
                        )
                    )
                }
                .then()
                .block()

            // ContentInteraction 초기화
            contentInteractionRepository.create(
                ContentInteraction(
                    contentId = savedPhotoContent.id!!,
                    likeCount = 0,
                    commentCount = 0,
                    shareCount = 0,
                    saveCount = 0,
                    viewCount = 0
                )
            ).block()

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(follower.id!!))
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path("${ApiPaths.API_V1_FEED}/following")
                        .queryParam("limit", 10)
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.content[0].contentType").isEqualTo("PHOTO")
                .jsonPath("$.content[0].photoUrls").isArray
                .jsonPath("$.content[0].photoUrls.length()").isEqualTo(4)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/feed/refresh - 피드 새로고침")
    inner class RefreshFeed {

        @Test
        @DisplayName("인증된 사용자가 새로고침 요청 시, 204 No Content를 반환한다")
        fun refreshFeed_WithAuthentication_ReturnsNoContent() {
            // Given: 사용자 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            // When & Then: 피드 새로고침 API 호출
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1_FEED}/refresh")
                .exchange()
                .expectStatus().isNoContent
        }

        @Test
        @DisplayName("인증되지 않은 요청 시, 401 Unauthorized를 반환한다")
        fun refreshFeed_WithoutAuth_ReturnsUnauthorized() {
            // When & Then: 인증 없이 API 호출
            webTestClient
                .post()
                .uri("${ApiPaths.API_V1_FEED}/refresh")
                .exchange()
                .expectStatus().isUnauthorized
        }
    }

    @Nested
    @DisplayName("POST /api/v1/feed/categories/{category}/refresh - 카테고리 피드 새로고침")
    inner class RefreshCategoryFeed {

        @Test
        @DisplayName("인증된 사용자가 카테고리 피드 새로고침 요청 시, Redis 캐시가 삭제된다")
        fun refreshCategoryFeed_WithAuthentication_ClearsCache() {
            // Given: 사용자 생성 및 테스트용 캐시 데이터 추가
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )
            val category = Category.PROGRAMMING
            val cacheKey = "feed:category:${user.id!!}:${category.name}:lang:ko:batch:0"
            val secondCacheKey = "feed:category:${user.id}:${category.name}:lang:en:batch:0"

            // Redis에 테스트 캐시 데이터 저장
            redisTemplate.opsForValue().set(cacheKey, "test-content-ids").block()
            redisTemplate.opsForValue().set(secondCacheKey, "test-content-ids").block()

            // 캐시가 저장되었는지 사전 확인
            val keyExistsBeforeRefresh = redisTemplate.hasKey(cacheKey).block()
            assertThat(keyExistsBeforeRefresh).isTrue()

            // When: 카테고리 피드 새로고침 API 호출
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1_FEED}/categories/{category}/refresh", category.name.lowercase())
                .exchange()
                .expectStatus().isNoContent

            // Then: Redis 캐시가 삭제되었는지 확인
            val keyExistsAfterRefresh = redisTemplate.hasKey(cacheKey).block()
            val secondKeyExistsAfterRefresh = redisTemplate.hasKey(secondCacheKey).block()
            assertThat(keyExistsAfterRefresh).isFalse()
            assertThat(secondKeyExistsAfterRefresh).isFalse()
        }

        @ParameterizedTest
        @EnumSource(value = Category::class, names = ["PROGRAMMING", "ART", "SCIENCE", "LANGUAGE"])
        @DisplayName("여러 카테고리로 새로고침 요청 시, 모두 204 No Content를 반환한다")
        fun refreshCategoryFeed_WithMultipleCategories_ReturnsNoContent(category: Category) {
            // Given: 사용자 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test-$category@example.com",
                providerId = "google-$category"
            )

            // When & Then: 카테고리 피드 새로고침 API 호출
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1_FEED}/categories/{category}/refresh", category.name.lowercase())
                .exchange()
                .expectStatus().isNoContent
        }

        @Test
        @DisplayName("인증되지 않은 요청 시, 401 Unauthorized를 반환한다")
        fun refreshCategoryFeed_WithoutAuth_ReturnsUnauthorized() {
            // When & Then: 인증 없이 API 호출
            webTestClient
                .post()
                .uri("${ApiPaths.API_V1_FEED}/categories/{category}/refresh", Category.PROGRAMMING.name.lowercase())
                .exchange()
                .expectStatus().isUnauthorized
        }
    }
}
