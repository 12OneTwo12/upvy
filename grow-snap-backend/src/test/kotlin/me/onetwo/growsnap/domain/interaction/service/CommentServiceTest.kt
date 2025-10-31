package me.onetwo.growsnap.domain.interaction.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import me.onetwo.growsnap.domain.analytics.dto.InteractionEventRequest
import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.analytics.service.AnalyticsService
import me.onetwo.growsnap.domain.interaction.dto.CommentRequest
import me.onetwo.growsnap.domain.interaction.exception.CommentException
import me.onetwo.growsnap.domain.interaction.model.Comment
import me.onetwo.growsnap.domain.interaction.repository.CommentRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime
import java.util.UUID

/**
 * 댓글 서비스 단위 테스트
 *
 * Repository의 동작을 모킹하여 Service 계층의 비즈니스 로직만 검증합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("CommentService 단위 테스트")
class CommentServiceTest {

    @MockK
    private lateinit var commentRepository: CommentRepository

    @MockK
    private lateinit var analyticsService: AnalyticsService

    @MockK
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @MockK
    private lateinit var userProfileRepository: UserProfileRepository

    @InjectMockKs
    private lateinit var commentService: CommentServiceImpl

    private val testUserId = UUID.randomUUID()
    private val testContentId = UUID.randomUUID()
    private val testCommentId = UUID.randomUUID()

    @Nested
    @DisplayName("createComment - 댓글 생성")
    inner class CreateComment {

        @Test
        @DisplayName("일반 댓글을 생성하면, Repository에 저장하고 Analytics 이벤트를 발행한다")
        fun createComment_Success() {
            // Given
            val request = CommentRequest(
                content = "Test comment",
                parentCommentId = null
            )

            val savedComment = Comment(
                id = testCommentId,
                contentId = testContentId,
                userId = testUserId,
                content = "Test comment",
                parentCommentId = null,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId,
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId
            )

            val userInfoMap = mapOf(
                testUserId to Pair("TestUser", "https://example.com/profile.jpg")
            )

            every { commentRepository.save(any()) } returns savedComment
            every { analyticsService.trackInteractionEvent(any(), any()) } returns Mono.empty()
            every { userProfileRepository.findUserInfosByUserIds(any()) } returns userInfoMap

            // When
            val result = commentService.createComment(testUserId, testContentId, request)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testCommentId.toString(), response.id)
                    assertEquals("Test comment", response.content)
                    assertEquals("TestUser", response.userNickname)
                    assertEquals("https://example.com/profile.jpg", response.userProfileImageUrl)
                }
                .verifyComplete()

            verify(exactly = 1) { commentRepository.save(any()) }
            verify(exactly = 1) {
                analyticsService.trackInteractionEvent(
                    testUserId,
                    InteractionEventRequest(
                        contentId = testContentId,
                        interactionType = InteractionType.COMMENT
                    )
                )
            }
        }

        @Test
        @DisplayName("부모 댓글이 존재하지 않으면, ParentCommentNotFoundException을 발생시킨다")
        fun createComment_ParentNotFound_ThrowsException() {
            // Given
            val parentCommentId = UUID.randomUUID()
            val request = CommentRequest(
                content = "Reply comment",
                parentCommentId = parentCommentId.toString()
            )

            every { commentRepository.findById(parentCommentId) } returns null

            // When & Then
            val result = commentService.createComment(testUserId, testContentId, request)

            StepVerifier.create(result)
                .expectErrorMatches { error ->
                    error is CommentException.ParentCommentNotFoundException &&
                        error.message.contains(parentCommentId.toString())
                }
                .verify()
        }
    }

    @Nested
    @DisplayName("getComments - 댓글 목록 조회")
    inner class GetComments {

        @Test
        @DisplayName("콘텐츠의 댓글을 조회하면, 계층 구조로 반환한다")
        fun getComments_ReturnsHierarchy() {
            // Given
            val parentComment = Comment(
                id = testCommentId,
                contentId = testContentId,
                userId = testUserId,
                content = "Parent comment",
                parentCommentId = null,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId,
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId
            )

            val replyCommentId = UUID.randomUUID()
            val replyComment = Comment(
                id = replyCommentId,
                contentId = testContentId,
                userId = testUserId,
                content = "Reply comment",
                parentCommentId = testCommentId,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId,
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId
            )

            val userInfoMap = mapOf(
                testUserId to Pair("TestUser", "https://example.com/profile.jpg")
            )

            every { commentRepository.findByContentId(testContentId) } returns listOf(parentComment, replyComment)
            every { userProfileRepository.findUserInfosByUserIds(setOf(testUserId)) } returns userInfoMap

            // When
            val result = commentService.getComments(testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testCommentId.toString(), response.id)
                    assertEquals("Parent comment", response.content)
                    assertEquals(1, response.replies.size)
                    assertEquals(replyCommentId.toString(), response.replies[0].id)
                    assertEquals("Reply comment", response.replies[0].content)
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("deleteComment - 댓글 삭제")
    inner class DeleteComment {

        @Test
        @DisplayName("댓글을 삭제하면, Repository에서 삭제하고 카운트를 감소시킨다")
        fun deleteComment_Success() {
            // Given
            val comment = Comment(
                id = testCommentId,
                contentId = testContentId,
                userId = testUserId,
                content = "Test comment",
                parentCommentId = null,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId,
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId
            )

            every { commentRepository.findById(testCommentId) } returns comment
            every { commentRepository.delete(testCommentId, testUserId) } returns Unit
            every { contentInteractionRepository.decrementCommentCount(testContentId) } returns Mono.empty()

            // When
            val result = commentService.deleteComment(testUserId, testCommentId)

            // Then
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { commentRepository.delete(testCommentId, testUserId) }
            verify(exactly = 1) { contentInteractionRepository.decrementCommentCount(testContentId) }
        }

        @Test
        @DisplayName("댓글이 존재하지 않으면, CommentNotFoundException을 발생시킨다")
        fun deleteComment_NotFound_ThrowsException() {
            // Given
            every { commentRepository.findById(testCommentId) } returns null

            // When & Then
            val result = commentService.deleteComment(testUserId, testCommentId)

            StepVerifier.create(result)
                .expectErrorMatches { error ->
                    error is CommentException.CommentNotFoundException &&
                        error.message.contains(testCommentId.toString())
                }
                .verify()
        }

        @Test
        @DisplayName("다른 사용자의 댓글을 삭제하려고 하면, CommentAccessDeniedException을 발생시킨다")
        fun deleteComment_OtherUser_ThrowsException() {
            // Given
            val otherUserId = UUID.randomUUID()
            val comment = Comment(
                id = testCommentId,
                contentId = testContentId,
                userId = otherUserId,
                content = "Test comment",
                parentCommentId = null,
                createdAt = LocalDateTime.now(),
                createdBy = otherUserId,
                updatedAt = LocalDateTime.now(),
                updatedBy = otherUserId
            )

            every { commentRepository.findById(testCommentId) } returns comment

            // When & Then
            StepVerifier.create(commentService.deleteComment(testUserId, testCommentId))
                .expectError(CommentException.CommentAccessDeniedException::class.java)
                .verify()
        }
    }
}
