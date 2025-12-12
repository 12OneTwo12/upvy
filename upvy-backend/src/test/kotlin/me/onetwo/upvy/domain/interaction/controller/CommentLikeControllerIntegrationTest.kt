package me.onetwo.upvy.domain.interaction.controller

import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import me.onetwo.upvy.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.upvy.domain.content.repository.ContentRepository
import me.onetwo.upvy.domain.interaction.dto.CommentRequest
import me.onetwo.upvy.domain.interaction.repository.CommentLikeRepository
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.domain.user.repository.UserProfileRepository
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.util.createContent
import me.onetwo.upvy.util.createUserWithProfile
import me.onetwo.upvy.util.mockUser
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
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("댓글 좋아요 Controller 통합 테스트")
class CommentLikeControllerIntegrationTest : AbstractIntegrationTest() {

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
    private lateinit var commentLikeRepository: CommentLikeRepository

    @Nested
    @DisplayName("POST /api/v1/comments/{commentId}/likes - 댓글 좋아요")
    inner class LikeComment {

        @Test
        @DisplayName("유효한 요청으로 댓글 좋아요 시, 200 OK와 좋아요 응답을 반환한다")
        fun likeComment_WithValidRequest_ReturnsCommentLikeResponse() {
            // Given: 사용자, 콘텐츠, 댓글 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            val commentRequest = CommentRequest(
                content = "Test comment",
                parentCommentId = null
            )

            val commentId = webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(commentRequest)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").exists()
                .returnResult()
                .responseBody?.let { String(it) }
                ?.let { UUID.fromString(it.substringAfter("\"id\":\"").substringBefore("\"")) }

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes", commentId.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.commentId").isEqualTo(commentId.toString())
                .jsonPath("$.isLiked").isEqualTo(true)

            // Then: 이벤트가 처리되어 DB에 좋아요가 저장되었는지 확인
            await.atMost(2, TimeUnit.SECONDS).untilAsserted {
                val like = commentLikeRepository.findByUserIdAndCommentId(user.id!!, commentId!!).block()
                assertThat(like).isNotNull
            }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/comments/{commentId}/likes - 댓글 좋아요 취소")
    inner class UnlikeComment {

        @Test
        @DisplayName("유효한 요청으로 댓글 좋아요 취소 시, 200 OK와 좋아요 응답을 반환한다")
        fun unlikeComment_WithValidRequest_ReturnsCommentLikeResponse() {
            // Given: 사용자, 콘텐츠, 댓글 생성 및 좋아요
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            val commentRequest = CommentRequest(
                content = "Test comment",
                parentCommentId = null
            )

            val commentId = webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(commentRequest)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").exists()
                .returnResult()
                .responseBody?.let { String(it) }
                ?.let { UUID.fromString(it.substringAfter("\"id\":\"").substringBefore("\"")) }

            // 좋아요 추가
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes", commentId.toString())
                .exchange()
                .expectStatus().isOk

            // When & Then: 좋아요 취소
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes", commentId.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.commentId").isEqualTo(commentId.toString())
                .jsonPath("$.isLiked").isEqualTo(false)

            // Then: 이벤트가 처리되어 DB에서 좋아요가 삭제되었는지 확인
            await.atMost(2, TimeUnit.SECONDS).untilAsserted {
                val like = commentLikeRepository.findByUserIdAndCommentId(user.id!!, commentId!!).block()
                assertThat(like).isNull()
            }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/comments/{commentId}/likes/count - 댓글 좋아요 수 조회")
    inner class GetLikeCount {

        @Test
        @DisplayName("댓글 좋아요 수 조회 시, 200 OK와 좋아요 수를 반환한다")
        fun getLikeCount_WithValidRequest_ReturnsLikeCount() {
            // Given: 사용자, 콘텐츠, 댓글 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            val commentRequest = CommentRequest(
                content = "Test comment",
                parentCommentId = null
            )

            val commentId = webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(commentRequest)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").exists()
                .returnResult()
                .responseBody?.let { String(it) }
                ?.let { UUID.fromString(it.substringAfter("\"id\":\"").substringBefore("\"")) }

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes/count", commentId.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.commentId").isEqualTo(commentId.toString())
                .jsonPath("$.likeCount").isNumber
        }
    }

    @Nested
    @DisplayName("GET /api/v1/comments/{commentId}/likes/check - 댓글 좋아요 상태 조회")
    inner class GetLikeStatus {

        @Test
        @DisplayName("댓글 좋아요 상태 조회 시, 사용자가 좋아요를 누른 경우 true를 반환한다")
        fun getLikeStatus_WhenUserLiked_ReturnsTrue() {
            // Given: 사용자, 콘텐츠, 댓글 생성 및 좋아요
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            val commentRequest = CommentRequest(
                content = "Test comment",
                parentCommentId = null
            )

            val commentId = webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(commentRequest)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").exists()
                .returnResult()
                .responseBody?.let { String(it) }
                ?.let { UUID.fromString(it.substringAfter("\"id\":\"").substringBefore("\"")) }

            // 좋아요 추가
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes", commentId.toString())
                .exchange()
                .expectStatus().isOk

            // When & Then: 좋아요 상태 조회
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes/check", commentId.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.commentId").isEqualTo(commentId.toString())
                .jsonPath("$.isLiked").isEqualTo(true)
        }

        @Test
        @DisplayName("댓글 좋아요 상태 조회 시, 사용자가 좋아요를 누르지 않은 경우 false를 반환한다")
        fun getLikeStatus_WhenUserNotLiked_ReturnsFalse() {
            // Given: 사용자, 콘텐츠, 댓글 생성 (좋아요 없음)
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            val commentRequest = CommentRequest(
                content = "Test comment",
                parentCommentId = null
            )

            val commentId = webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(commentRequest)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").exists()
                .returnResult()
                .responseBody?.let { String(it) }
                ?.let { UUID.fromString(it.substringAfter("\"id\":\"").substringBefore("\"")) }

            // When & Then: 좋아요 상태 조회
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes/check", commentId.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.commentId").isEqualTo(commentId.toString())
                .jsonPath("$.isLiked").isEqualTo(false)
        }
    }
}
