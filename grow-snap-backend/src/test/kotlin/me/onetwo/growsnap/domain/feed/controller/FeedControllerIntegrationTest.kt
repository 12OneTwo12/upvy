package me.onetwo.growsnap.domain.feed.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.util.createContent
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
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
class FeedControllerIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

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
}
