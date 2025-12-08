package me.onetwo.upvy.domain.interaction.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.interaction.dto.CommentLikeCountResponse
import me.onetwo.upvy.domain.interaction.dto.CommentLikeResponse
import me.onetwo.upvy.domain.interaction.dto.CommentLikeStatusResponse
import me.onetwo.upvy.domain.interaction.service.CommentLikeService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.RestDocsConfiguration
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.util.UUID

@WebFluxTest(CommentLikeController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("댓글 좋아요 Controller 테스트")
class CommentLikeControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var commentLikeService: CommentLikeService

    @Nested
    @DisplayName("POST /api/v1/comments/{commentId}/likes - 댓글 좋아요")
    inner class LikeComment {

        @Test
        @DisplayName("유효한 요청으로 댓글 좋아요 시, 200 OK와 좋아요 응답을 반환한다")
        fun likeComment_WithValidRequest_ReturnsCommentLikeResponse() {
            // Given: 댓글 좋아요 요청
            val userId = UUID.randomUUID()
            val commentId = UUID.randomUUID().toString()
            val response = CommentLikeResponse(
                commentId = commentId,
                likeCount = 10,
                isLiked = true
            )

            every { commentLikeService.likeComment(userId, UUID.fromString(commentId)) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes", commentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.commentId").isEqualTo(commentId)
                .jsonPath("$.isLiked").isEqualTo(true)
                .jsonPath("$.likeCount").isEqualTo(10)
                .consumeWith(
                    document(
                        "comment-like-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("commentId").description("댓글 ID")
                        ),
                        responseFields(
                            fieldWithPath("commentId").description("댓글 ID"),
                            fieldWithPath("isLiked").description("좋아요 여부"),
                            fieldWithPath("likeCount").description("좋아요 수")
                        )
                    )
                )

            verify(exactly = 1) { commentLikeService.likeComment(userId, UUID.fromString(commentId)) }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/comments/{commentId}/likes - 댓글 좋아요 취소")
    inner class UnlikeComment {

        @Test
        @DisplayName("유효한 요청으로 댓글 좋아요 취소 시, 200 OK와 좋아요 응답을 반환한다")
        fun unlikeComment_WithValidRequest_ReturnsCommentLikeResponse() {
            // Given: 댓글 좋아요 취소 요청
            val userId = UUID.randomUUID()
            val commentId = UUID.randomUUID().toString()
            val response = CommentLikeResponse(
                commentId = commentId,
                likeCount = 9,
                isLiked = false
            )

            every { commentLikeService.unlikeComment(userId, UUID.fromString(commentId)) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes", commentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.commentId").isEqualTo(commentId)
                .jsonPath("$.isLiked").isEqualTo(false)
                .jsonPath("$.likeCount").isEqualTo(9)
                .consumeWith(
                    document(
                        "comment-like-delete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("commentId").description("댓글 ID")
                        ),
                        responseFields(
                            fieldWithPath("commentId").description("댓글 ID"),
                            fieldWithPath("isLiked").description("좋아요 여부"),
                            fieldWithPath("likeCount").description("좋아요 수")
                        )
                    )
                )

            verify(exactly = 1) { commentLikeService.unlikeComment(userId, UUID.fromString(commentId)) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/comments/{commentId}/likes/count - 댓글 좋아요 수 조회")
    inner class GetLikeCount {

        @Test
        @DisplayName("댓글 좋아요 수 조회 시, 200 OK와 좋아요 수를 반환한다")
        fun getLikeCount_WithValidRequest_ReturnsLikeCount() {
            // Given: 댓글 좋아요 수 조회 요청
            val userId = UUID.randomUUID()
            val commentId = UUID.randomUUID().toString()
            val response = CommentLikeCountResponse(
                commentId = commentId,
                likeCount = 15
            )

            every { commentLikeService.getLikeCount(UUID.fromString(commentId)) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes/count", commentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.commentId").isEqualTo(commentId)
                .jsonPath("$.likeCount").isEqualTo(15)
                .consumeWith(
                    document(
                        "comment-like-count-get",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("commentId").description("댓글 ID")
                        ),
                        responseFields(
                            fieldWithPath("commentId").description("댓글 ID"),
                            fieldWithPath("likeCount").description("좋아요 수")
                        )
                    )
                )

            verify(exactly = 1) { commentLikeService.getLikeCount(UUID.fromString(commentId)) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/comments/{commentId}/likes/check - 댓글 좋아요 상태 조회")
    inner class GetLikeStatus {

        @Test
        @DisplayName("댓글 좋아요 상태 조회 시, 사용자가 좋아요를 누른 경우 true를 반환한다")
        fun getLikeStatus_WhenUserLiked_ReturnsTrue() {
            // Given: 사용자가 댓글에 좋아요를 누른 상태
            val userId = UUID.randomUUID()
            val commentId = UUID.randomUUID().toString()
            val response = CommentLikeStatusResponse(
                commentId = commentId,
                isLiked = true
            )

            every { commentLikeService.getLikeStatus(userId, UUID.fromString(commentId)) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes/check", commentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.commentId").isEqualTo(commentId)
                .jsonPath("$.isLiked").isEqualTo(true)
                .consumeWith(
                    document(
                        "comment-like-status-check",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("commentId").description("댓글 ID")
                        ),
                        responseFields(
                            fieldWithPath("commentId").description("댓글 ID"),
                            fieldWithPath("isLiked").description("좋아요 여부 (true: 좋아요, false: 좋아요 안 함)")
                        )
                    )
                )

            verify(exactly = 1) { commentLikeService.getLikeStatus(userId, UUID.fromString(commentId)) }
        }

        @Test
        @DisplayName("댓글 좋아요 상태 조회 시, 사용자가 좋아요를 누르지 않은 경우 false를 반환한다")
        fun getLikeStatus_WhenUserNotLiked_ReturnsFalse() {
            // Given: 사용자가 댓글에 좋아요를 누르지 않은 상태
            val userId = UUID.randomUUID()
            val commentId = UUID.randomUUID().toString()
            val response = CommentLikeStatusResponse(
                commentId = commentId,
                isLiked = false
            )

            every { commentLikeService.getLikeStatus(userId, UUID.fromString(commentId)) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/likes/check", commentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.commentId").isEqualTo(commentId)
                .jsonPath("$.isLiked").isEqualTo(false)

            verify(exactly = 1) { commentLikeService.getLikeStatus(userId, UUID.fromString(commentId)) }
        }
    }
}
