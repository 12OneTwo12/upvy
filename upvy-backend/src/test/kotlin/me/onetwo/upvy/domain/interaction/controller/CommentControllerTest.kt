package me.onetwo.upvy.domain.interaction.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.interaction.dto.CommentListResponse
import me.onetwo.upvy.domain.interaction.dto.CommentRequest
import me.onetwo.upvy.domain.interaction.dto.CommentResponse
import me.onetwo.upvy.domain.interaction.exception.CommentException
import me.onetwo.upvy.domain.interaction.service.CommentService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.infrastructure.config.RestDocsConfiguration
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@WebFluxTest(CommentController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("댓글 Controller 테스트")
class CommentControllerTest : BaseReactiveTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var commentService: CommentService

    @Nested
    @DisplayName("POST /api/v1/videos/{contentId}/comments - 댓글 작성")
    inner class CreateComment {

        @Test
        @DisplayName("유효한 요청으로 댓글 작성 시, 201 Created와 댓글 정보를 반환한다")
        fun createComment_WithValidRequest_ReturnsCreatedComment() {
            // Given: 댓글 작성 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()
            val request = CommentRequest(
                content = "Test comment",
                parentCommentId = null
            )

            val response = CommentResponse(
                id = UUID.randomUUID().toString(),
                contentId = contentId,
                userId = userId.toString(),
                userNickname = "TestUser",
                userProfileImageUrl = null,
                content = "Test comment",
                parentCommentId = null,
                createdAt = "2025-10-23T17:30:00",
                replyCount = 0,
                likeCount = 0,
                isLiked = false
            )

            every { commentService.createComment(userId, UUID.fromString(contentId), request) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", contentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isEqualTo(response.id)
                .jsonPath("$.content").isEqualTo("Test comment")
                .jsonPath("$.userNickname").isEqualTo("TestUser")
                .consumeWith(
                    document(
                        "comment-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        ),
                        requestFields(
                            fieldWithPath("content").description("댓글 내용"),
                            fieldWithPath("parentCommentId").description("부모 댓글 ID (대댓글인 경우)").optional()
                        ),
                        responseFields(
                            fieldWithPath("id").description("댓글 ID"),
                            fieldWithPath("contentId").description("콘텐츠 ID"),
                            fieldWithPath("userId").description("작성자 ID"),
                            fieldWithPath("userNickname").description("작성자 닉네임"),
                            fieldWithPath("userProfileImageUrl").description("작성자 프로필 이미지 URL").optional(),
                            fieldWithPath("content").description("댓글 내용"),
                            fieldWithPath("parentCommentId").description("부모 댓글 ID").optional(),
                            fieldWithPath("createdAt").description("작성 시각"),
                            fieldWithPath("replyCount").description("대댓글 개수"),
                            fieldWithPath("likeCount").description("좋아요 수"),
                            fieldWithPath("isLiked").description("현재 사용자의 좋아요 여부")
                        )
                    )
                )

            verify(exactly = 1) { commentService.createComment(userId, UUID.fromString(contentId), request) }
        }

        @Test
        @DisplayName("댓글 내용이 비어있으면, 400 Bad Request를 반환한다")
        fun createComment_WithEmptyContent_ReturnsBadRequest() {
            // Given: 빈 내용
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()
            val request = mapOf(
                "content" to "",
                "parentCommentId" to null
            )

            // When & Then: 400 응답 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/contents/$contentId/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest

            verify(exactly = 0) { commentService.createComment(any(), any(), any()) }
        }

        @Test
        @DisplayName("대댓글 작성 시, 부모 댓글 ID가 포함된다")
        fun createComment_WithParentId_CreatesReply() {
            // Given: 대댓글 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()
            val parentCommentId = UUID.randomUUID().toString()
            val request = CommentRequest(
                content = "Reply comment",
                parentCommentId = parentCommentId
            )

            val response = CommentResponse(
                id = UUID.randomUUID().toString(),
                contentId = contentId,
                userId = userId.toString(),
                userNickname = "TestUser",
                userProfileImageUrl = null,
                content = "Reply comment",
                parentCommentId = parentCommentId,
                createdAt = "2025-10-23T17:30:00",
                replyCount = 0,
                likeCount = 0,
                isLiked = false
            )

            every { commentService.createComment(userId, UUID.fromString(contentId), request) } returns Mono.just(response)

            // When & Then: 대댓글 작성 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/contents/$contentId/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.parentCommentId").isEqualTo(parentCommentId)

            verify(exactly = 1) { commentService.createComment(userId, UUID.fromString(contentId), request) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/videos/{contentId}/comments - 댓글 목록 조회")
    inner class GetComments {

        @Test
        @DisplayName("콘텐츠의 댓글 목록을 조회하면, 계층 구조로 반환된다")
        fun getComments_ReturnsHierarchicalComments() {
            // Given: 부모 댓글과 대댓글
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()
            val parentCommentId = UUID.randomUUID().toString()

            val reply = CommentResponse(
                id = UUID.randomUUID().toString(),
                contentId = contentId,
                userId = UUID.randomUUID().toString(),
                userNickname = "User2",
                userProfileImageUrl = null,
                content = "Reply comment",
                parentCommentId = parentCommentId,
                createdAt = "2025-10-23T17:31:00",
                replyCount = 0,
                likeCount = 0,
                isLiked = false
            )

            val parentComment = CommentResponse(
                id = parentCommentId,
                contentId = contentId,
                userId = UUID.randomUUID().toString(),
                userNickname = "User1",
                userProfileImageUrl = null,
                content = "Parent comment",
                parentCommentId = null,
                createdAt = "2025-10-23T17:30:00",
                replyCount = 1,
                likeCount = 0,
                isLiked = false
            )

            val response = CommentListResponse(
                comments = listOf(parentComment),
                hasNext = false,
                nextCursor = null
            )

            every { commentService.getComments(null, UUID.fromString(contentId), null, 20) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/comments", contentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comments[0].id").isEqualTo(parentCommentId)
                .jsonPath("$.comments[0].content").isEqualTo("Parent comment")
                .jsonPath("$.comments[0].replyCount").isEqualTo(1)
                .jsonPath("$.hasNext").isEqualTo(false)
                .consumeWith(
                    document(
                        "comment-list",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        ),
                        queryParameters(
                            parameterWithName("cursor").description("페이징 커서 (댓글 ID)").optional(),
                            parameterWithName("limit").description("조회 개수 (기본값: 20)").optional()
                        ),
                        responseFields(
                            fieldWithPath("comments[]").description("댓글 목록 (인기순 정렬: 좋아요 수 + 대댓글 수 내림차순, 같은 점수면 오래된 순)"),
                            fieldWithPath("comments[].id").description("댓글 ID"),
                            fieldWithPath("comments[].contentId").description("콘텐츠 ID"),
                            fieldWithPath("comments[].userId").description("작성자 ID"),
                            fieldWithPath("comments[].userNickname").description("작성자 닉네임"),
                            fieldWithPath("comments[].userProfileImageUrl").description("작성자 프로필 이미지 URL").optional(),
                            fieldWithPath("comments[].content").description("댓글 내용"),
                            fieldWithPath("comments[].parentCommentId").description("부모 댓글 ID (null이면 최상위 댓글)").optional(),
                            fieldWithPath("comments[].createdAt").description("작성 시각"),
                            fieldWithPath("comments[].replyCount").description("대댓글 개수 (0이면 대댓글 없음)"),
                            fieldWithPath("comments[].likeCount").description("좋아요 수"),
                            fieldWithPath("comments[].isLiked").description("현재 사용자의 좋아요 여부"),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                            fieldWithPath("nextCursor").description("다음 페이지 커서").optional()
                        )
                    )
                )

            verify(exactly = 1) { commentService.getComments(null, UUID.fromString(contentId), null, 20) }
        }

        @Test
        @DisplayName("댓글이 없으면, 빈 comments 배열을 반환한다")
        fun getComments_WithNoComments_ReturnsEmptyList() {
            // Given: 댓글이 없는 콘텐츠
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()

            val response = CommentListResponse(
                comments = emptyList(),
                hasNext = false,
                nextCursor = null
            )

            every { commentService.getComments(null, UUID.fromString(contentId), null, 20) } returns Mono.just(response)

            // When & Then: 빈 배열 반환 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1}/contents/$contentId/comments")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comments.length()").isEqualTo(0)
                .jsonPath("$.hasNext").isEqualTo(false)

            verify(exactly = 1) { commentService.getComments(null, UUID.fromString(contentId), null, 20) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/comments/{commentId}/replies - 대댓글 목록 조회")
    inner class GetReplies {

        @Test
        @DisplayName("대댓글 목록을 페이징으로 조회하면, CommentListResponse를 반환한다")
        fun getReplies_WithPagination_ReturnsCommentListResponse() {
            // Given: 대댓글이 있는 댓글
            val userId = UUID.randomUUID()
            val parentCommentId = UUID.randomUUID().toString()
            val replyId = UUID.randomUUID().toString()

            val reply = CommentResponse(
                id = replyId,
                contentId = UUID.randomUUID().toString(),
                userId = userId.toString(),
                userNickname = "ReplyUser",
                userProfileImageUrl = null,
                content = "Reply comment",
                parentCommentId = parentCommentId,
                createdAt = "2025-10-23T17:31:00",
                replyCount = 0,
                likeCount = 0,
                isLiked = false
            )

            val response = CommentListResponse(
                comments = listOf(reply),
                hasNext = false,
                nextCursor = null
            )

            every { commentService.getReplies(null, UUID.fromString(parentCommentId), null, 20) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1}/comments/{commentId}/replies", parentCommentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comments[0].id").isEqualTo(replyId)
                .jsonPath("$.comments[0].content").isEqualTo("Reply comment")
                .jsonPath("$.comments[0].parentCommentId").isEqualTo(parentCommentId)
                .jsonPath("$.hasNext").isEqualTo(false)
                .consumeWith(
                    document(
                        "comment-replies",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("commentId").description("부모 댓글 ID")
                        ),
                        queryParameters(
                            parameterWithName("cursor").description("페이징 커서 (댓글 ID)").optional(),
                            parameterWithName("limit").description("조회 개수 (기본값: 20)").optional()
                        ),
                        responseFields(
                            fieldWithPath("comments[]").description("대댓글 목록 (작성 시간 오름차순 정렬)"),
                            fieldWithPath("comments[].id").description("댓글 ID"),
                            fieldWithPath("comments[].contentId").description("콘텐츠 ID"),
                            fieldWithPath("comments[].userId").description("작성자 ID"),
                            fieldWithPath("comments[].userNickname").description("작성자 닉네임"),
                            fieldWithPath("comments[].userProfileImageUrl").description("작성자 프로필 이미지 URL").optional(),
                            fieldWithPath("comments[].content").description("댓글 내용"),
                            fieldWithPath("comments[].parentCommentId").description("부모 댓글 ID"),
                            fieldWithPath("comments[].createdAt").description("작성 시각"),
                            fieldWithPath("comments[].replyCount").description("대댓글 개수 (대댓글의 대댓글은 지원하지 않으므로 항상 0)"),
                            fieldWithPath("comments[].likeCount").description("좋아요 수"),
                            fieldWithPath("comments[].isLiked").description("현재 사용자의 좋아요 여부"),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                            fieldWithPath("nextCursor").description("다음 페이지 커서").optional()
                        )
                    )
                )

            verify(exactly = 1) { commentService.getReplies(null, UUID.fromString(parentCommentId), null, 20) }
        }

        @Test
        @DisplayName("대댓글이 없으면, 빈 comments 배열을 반환한다")
        fun getReplies_WithNoReplies_ReturnsEmptyList() {
            // Given: 대댓글이 없는 댓글
            val userId = UUID.randomUUID()
            val parentCommentId = UUID.randomUUID().toString()

            val response = CommentListResponse(
                comments = emptyList(),
                hasNext = false,
                nextCursor = null
            )

            every { commentService.getReplies(null, UUID.fromString(parentCommentId), null, 20) } returns Mono.just(response)

            // When & Then: 빈 배열 반환 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1}/comments/$parentCommentId/replies")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.comments.length()").isEqualTo(0)
                .jsonPath("$.hasNext").isEqualTo(false)

            verify(exactly = 1) { commentService.getReplies(null, UUID.fromString(parentCommentId), null, 20) }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/comments/{commentId} - 댓글 삭제")
    inner class DeleteComment {

        @Test
        @DisplayName("자신의 댓글을 삭제하면, 204 No Content를 반환한다")
        fun deleteComment_OwnComment_ReturnsNoContent() {
            // Given: 댓글 ID
            val userId = UUID.randomUUID()
            val commentId = UUID.randomUUID().toString()

            every { commentService.deleteComment(userId, UUID.fromString(commentId)) } returns Mono.empty()

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("${ApiPaths.API_V1}/comments/{commentId}", commentId)
                .exchange()
                .expectStatus().isNoContent
                .expectBody()
                .consumeWith(
                    document(
                        "comment-delete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("commentId").description("댓글 ID")
                        )
                    )
                )

            verify(exactly = 1) { commentService.deleteComment(userId, UUID.fromString(commentId)) }
        }

        @Test
        @DisplayName("존재하지 않는 댓글을 삭제하면, 404 Not Found를 반환한다")
        fun deleteComment_NonExistentComment_ReturnsNotFound() {
            // Given: 존재하지 않는 댓글 ID
            val userId = UUID.randomUUID()
            val commentId = UUID.randomUUID().toString()

            every { commentService.deleteComment(userId, UUID.fromString(commentId)) } returns
                Mono.error(CommentException.CommentNotFoundException(UUID.fromString(commentId)))

            // When & Then: 404 응답 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("${ApiPaths.API_V1}/comments/$commentId")
                .exchange()
                .expectStatus().isNotFound

            verify(exactly = 1) { commentService.deleteComment(userId, UUID.fromString(commentId)) }
        }

        @Test
        @DisplayName("다른 사용자의 댓글을 삭제하면, 403 Forbidden을 반환한다")
        fun deleteComment_OthersComment_ReturnsForbidden() {
            // Given: 다른 사용자의 댓글
            val userId = UUID.randomUUID()
            val commentId = UUID.randomUUID().toString()

            every { commentService.deleteComment(userId, UUID.fromString(commentId)) } returns
                Mono.error(CommentException.CommentAccessDeniedException(UUID.fromString(commentId)))

            // When & Then: 403 응답 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("${ApiPaths.API_V1}/comments/$commentId")
                .exchange()
                .expectStatus().isForbidden

            verify(exactly = 1) { commentService.deleteComment(userId, UUID.fromString(commentId)) }
        }
    }
}
