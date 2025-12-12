package me.onetwo.upvy.domain.interaction.controller

import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import me.onetwo.upvy.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.upvy.domain.content.repository.ContentRepository
import me.onetwo.upvy.domain.interaction.dto.CommentRequest
import me.onetwo.upvy.domain.interaction.repository.CommentRepository
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
@DisplayName("댓글 Controller 통합 테스트")
class CommentControllerIntegrationTest : AbstractIntegrationTest() {

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
    private lateinit var commentRepository: CommentRepository

    @Nested
    @DisplayName("POST /api/v1/contents/{contentId}/comments - 댓글 작성")
    inner class CreateComment {

        @Test
        @DisplayName("유효한 요청으로 댓글 작성 시, 201 Created와 댓글 정보를 반환한다")
        fun createComment_WithValidRequest_ReturnsCreatedComment() {
            // Given: 사용자와 콘텐츠 생성
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

            val request = CommentRequest(
                content = "Test comment",
                parentCommentId = null
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isNotEmpty
                .jsonPath("$.content").isEqualTo("Test comment")
                .jsonPath("$.userId").isEqualTo(user.id!!.toString())

            // Then: 이벤트가 처리되어 DB에 댓글이 저장되었는지 확인
            await.atMost(2, TimeUnit.SECONDS).untilAsserted {
                val comments = commentRepository.findTopLevelCommentsByContentId(content.id!!, null, 100).collectList().block()!!
                assertThat(comments).isNotEmpty
            }
        }

        @Test
        @DisplayName("댓글 내용이 비어있으면, 400 Bad Request를 반환한다")
        fun createComment_WithEmptyContent_ReturnsBadRequest() {
            // Given: 사용자와 콘텐츠 생성
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

            val request = mapOf(
                "content" to "",
                "parentCommentId" to null
            )

            // When & Then: 400 응답 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("대댓글 작성 시, 부모 댓글 ID가 포함된다")
        fun createComment_WithParentId_CreatesReply() {
            // Given: 사용자, 콘텐츠, 부모 댓글 생성
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

            // 부모 댓글 먼저 작성
            val parentRequest = CommentRequest(
                content = "Parent comment",
                parentCommentId = null
            )

            val parentCommentId = webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(parentRequest)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").exists()
                .returnResult()
                .responseBody?.let { String(it) }
                ?.let { UUID.fromString(it.substringAfter("\"id\":\"").substringBefore("\"")) }

            // 대댓글 작성
            val replyRequest = CommentRequest(
                content = "Reply comment",
                parentCommentId = parentCommentId.toString()
            )

            // When & Then: 대댓글 작성 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(replyRequest)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.parentCommentId").isEqualTo(parentCommentId.toString())
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId}/comments - 댓글 목록 조회")
    inner class GetComments {

        @Test
        @DisplayName("콘텐츠의 댓글 목록을 조회하면, 계층 구조로 반환된다")
        fun getComments_ReturnsHierarchicalComments() {
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

            // 댓글 작성
            val request = CommentRequest(
                content = "Parent comment",
                parentCommentId = null
            )

            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comments").isArray
                .jsonPath("$.hasNext").isBoolean
        }

        @Test
        @DisplayName("댓글이 없으면, 빈 comments 배열을 반환한다")
        fun getComments_WithNoComments_ReturnsEmptyList() {
            // Given: 사용자와 콘텐츠만 생성 (댓글 없음)
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

            // When & Then: 빈 배열 반환 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comments.length()").isEqualTo(0)
                .jsonPath("$.hasNext").isEqualTo(false)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/comments/{commentId}/replies - 대댓글 목록 조회")
    inner class GetReplies {

        @Test
        @DisplayName("대댓글 목록을 페이징하여 조회하면, 대댓글 목록이 반환된다")
        fun getReplies_WithPagination_ReturnsCommentListResponse() {
            // Given: 사용자, 콘텐츠, 부모 댓글, 대댓글 생성
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

            // 부모 댓글 작성
            val parentRequest = CommentRequest(
                content = "Parent comment",
                parentCommentId = null
            )

            val parentCommentId = webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(parentRequest)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").exists()
                .returnResult()
                .responseBody?.let { String(it) }
                ?.let { it.substringAfter("\"id\":\"").substringBefore("\"") }

            // 대댓글 여러 개 작성
            for (i in 1..3) {
                val replyRequest = CommentRequest(
                    content = "Reply comment $i",
                    parentCommentId = parentCommentId
                )

                webTestClient
                    .mutateWith(mockUser(user.id!!))
                    .post()
                    .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(replyRequest)
                    .exchange()
                    .expectStatus().isCreated
            }

            // When & Then: 대댓글 목록 조회
            webTestClient
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path("${ApiPaths.API_V1}/comments/{commentId}/replies")
                        .queryParam("limit", 10)
                        .build(parentCommentId)
                }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comments").isArray
                .jsonPath("$.comments.length()").isEqualTo(3)
                .jsonPath("$.hasNext").isBoolean
        }

        @Test
        @DisplayName("대댓글이 없으면, 빈 comments 배열을 반환한다")
        fun getReplies_WithNoReplies_ReturnsEmptyList() {
            // Given: 사용자, 콘텐츠, 부모 댓글만 생성 (대댓글 없음)
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

            // 부모 댓글만 작성
            val parentRequest = CommentRequest(
                content = "Parent comment",
                parentCommentId = null
            )

            val parentCommentId = webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(parentRequest)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").exists()
                .returnResult()
                .responseBody?.let { String(it) }
                ?.let { it.substringAfter("\"id\":\"").substringBefore("\"") }

            // When & Then: 대댓글 목록 조회 - 빈 배열 반환
            webTestClient
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path("${ApiPaths.API_V1}/comments/{commentId}/replies")
                        .queryParam("limit", 10)
                        .build(parentCommentId)
                }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comments").isArray
                .jsonPath("$.comments.length()").isEqualTo(0)
                .jsonPath("$.hasNext").isEqualTo(false)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/comments/{commentId} - 댓글 삭제")
    inner class DeleteComment {

        @Test
        @DisplayName("자신의 댓글을 삭제하면, 204 No Content를 반환한다")
        fun deleteComment_OwnComment_ReturnsNoContent() {
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

            val request = CommentRequest(
                content = "Test comment",
                parentCommentId = null
            )

            val commentId = webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").exists()
                .returnResult()
                .responseBody?.let { String(it) }
                ?.let { it.substringAfter("\"id\":\"").substringBefore("\"") }

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1}/comments/{commentId}", commentId)
                .exchange()
                .expectStatus().isNoContent
        }

        @Test
        @DisplayName("존재하지 않는 댓글 삭제 시, 404 Not Found를 반환한다")
        fun deleteComment_NonExistentComment_ReturnsNotFound() {
            // Given: 사용자만 생성 (댓글 없음)
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com"
            )

            val nonExistentCommentId = UUID.randomUUID().toString()

            // When & Then: 존재하지 않는 댓글 삭제 시도
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1}/comments/{commentId}", nonExistentCommentId)
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        @DisplayName("다른 사용자의 댓글 삭제 시, 403 Forbidden을 반환한다")
        fun deleteComment_OthersComment_ReturnsForbidden() {
            // Given: 두 명의 사용자와 콘텐츠 생성
            val (commentAuthor, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "author@example.com"
            )

            val (otherUser, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "other@example.com"
            )

            val content = createContent(
                contentRepository,
                creatorId = commentAuthor.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // 첫 번째 사용자가 댓글 작성
            val request = CommentRequest(
                content = "Test comment",
                parentCommentId = null
            )

            val commentId = webTestClient
                .mutateWith(mockUser(commentAuthor.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", content.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").exists()
                .returnResult()
                .responseBody?.let { String(it) }
                ?.let { it.substringAfter("\"id\":\"").substringBefore("\"") }

            // When & Then: 다른 사용자가 댓글 삭제 시도
            webTestClient
                .mutateWith(mockUser(otherUser.id!!))
                .delete()
                .uri("${ApiPaths.API_V1}/comments/{commentId}", commentId)
                .exchange()
                .expectStatus().isForbidden
        }
    }
}
