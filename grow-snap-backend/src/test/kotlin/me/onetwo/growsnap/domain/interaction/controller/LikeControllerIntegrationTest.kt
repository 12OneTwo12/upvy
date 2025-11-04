package me.onetwo.growsnap.domain.interaction.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import me.onetwo.growsnap.domain.interaction.repository.UserLikeRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.util.createContent
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("좋아요 Controller 통합 테스트")
class LikeControllerIntegrationTest {

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
    private lateinit var userLikeRepository: UserLikeRepository

    @Nested
    @DisplayName("POST /api/v1/contents/{contentId}/like - 좋아요")
    inner class LikeContent {

        @Test
        @DisplayName("유효한 요청으로 좋아요 시, 200 OK와 좋아요 응답을 반환한다")
        fun likeContent_WithValidRequest_ReturnsLikeResponse() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // When & Then: API 호출 및 검증 (likeCount는 비동기 이벤트 처리 후 업데이트되므로 즉시 확인하지 않음)
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/like", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(content.id.toString())
                .jsonPath("$.isLiked").isEqualTo(true)

            // Then: 이벤트가 처리되어 DB에 좋아요가 저장되고 카운트가 증가했는지 확인
            await.atMost(2, TimeUnit.SECONDS).untilAsserted {
                val like = userLikeRepository.findByUserIdAndContentId(user.id!!, content.id!!).block()
                assertThat(like).isNotNull

                val likeCount = contentInteractionRepository.getLikeCount(content.id!!).block()
                assertThat(likeCount).isEqualTo(1)
            }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/contents/{contentId}/like - 좋아요 취소")
    inner class UnlikeContent {

        @Test
        @DisplayName("유효한 요청으로 좋아요 취소 시, 200 OK와 좋아요 응답을 반환한다")
        fun unlikeContent_WithValidRequest_ReturnsLikeResponse() {
            // Given: 사용자, 콘텐츠 생성 및 좋아요
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // 좋아요 추가
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/like", content.id!!.toString())
                .exchange()
                .expectStatus().isOk

            // When & Then: 좋아요 취소
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/like", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(content.id!!.toString())
                .jsonPath("$.isLiked").isEqualTo(false)

            // Then: 이벤트가 처리되어 DB에서 좋아요가 삭제되고 카운트가 감소했는지 확인
            await.atMost(2, TimeUnit.SECONDS).untilAsserted {
                val like = userLikeRepository.findByUserIdAndContentId(user.id!!, content.id!!).block()
                assertThat(like).isNull()

                val likeCount = contentInteractionRepository.getLikeCount(content.id!!).block()
                assertThat(likeCount).isEqualTo(0)
            }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId}/likes - 좋아요 수 조회")
    inner class GetLikeCount {

        @Test
        @DisplayName("좋아요 수 조회 시, 200 OK와 좋아요 수를 반환한다")
        fun getLikeCount_WithValidRequest_ReturnsLikeCount() {
            // Given: 사용자, 콘텐츠 생성 및 좋아요
            val (user1, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test1@example.com",
                providerId = "google-123",
                nickname = "user1${System.currentTimeMillis() % 100000}"
            )

            val (user2, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test2@example.com",
                providerId = "google-456",
                nickname = "user2${System.currentTimeMillis() % 100000}"
            )

            val content = createContent(
                contentRepository,
                creatorId = user1.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // 두 사용자가 좋아요
            webTestClient
                .mutateWith(mockUser(user1.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/like", content.id!!.toString())
                .exchange()
                .expectStatus().isOk

            webTestClient
                .mutateWith(mockUser(user2.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/like", content.id!!.toString())
                .exchange()
                .expectStatus().isOk

            // Then: 비동기 이벤트가 처리될 때까지 대기
            await.atMost(2, TimeUnit.SECONDS).untilAsserted {
                val likeCount = contentInteractionRepository.getLikeCount(content.id!!).block()
                assertThat(likeCount).isEqualTo(2)
            }

            // When & Then: 좋아요 수 조회
            webTestClient
                .mutateWith(mockUser(user1.id!!))
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/likes", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(content.id!!.toString())
                .jsonPath("$.likeCount").isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId}/like/status - 좋아요 상태 조회")
    inner class GetLikeStatus {

        @Test
        @DisplayName("좋아요 상태 조회 시, 사용자가 좋아요를 누른 경우 true를 반환한다")
        fun getLikeStatus_WhenUserLiked_ReturnsTrue() {
            // Given: 사용자, 콘텐츠 생성 및 좋아요
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // 좋아요 추가
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/like", content.id!!.toString())
                .exchange()
                .expectStatus().isOk

            // When & Then: 좋아요 상태 조회
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/like/status", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(content.id!!.toString())
                .jsonPath("$.isLiked").isEqualTo(true)
        }

        @Test
        @DisplayName("좋아요 상태 조회 시, 사용자가 좋아요를 누르지 않은 경우 false를 반환한다")
        fun getLikeStatus_WhenUserNotLiked_ReturnsFalse() {
            // Given: 사용자와 콘텐츠 생성 (좋아요 없음)
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // When & Then: 좋아요 상태 조회
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/like/status", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(content.id!!.toString())
                .jsonPath("$.isLiked").isEqualTo(false)
        }
    }
}
