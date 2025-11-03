package me.onetwo.growsnap.domain.interaction.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.interaction.dto.CommentRequest
import me.onetwo.growsnap.domain.interaction.event.CommentCreatedEvent
import me.onetwo.growsnap.domain.interaction.event.CommentDeletedEvent
import me.onetwo.growsnap.domain.interaction.exception.CommentException
import me.onetwo.growsnap.domain.interaction.model.Comment
import me.onetwo.growsnap.domain.interaction.repository.CommentLikeRepository
import me.onetwo.growsnap.domain.interaction.repository.CommentRepository
import me.onetwo.growsnap.domain.user.dto.UserInfo
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import reactor.core.publisher.Flux
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
    private lateinit var commentLikeRepository: CommentLikeRepository

    @MockK
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

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
        @DisplayName("일반 댓글을 생성하면, Repository에 저장하고 CommentCreatedEvent를 발행한다")
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
                createdBy = testUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId.toString()
            )

            val userInfoMap = mapOf(
                testUserId to UserInfo("TestUser", "https://example.com/profile.jpg")
            )

            every { commentRepository.save(any()) } returns Mono.just(savedComment)
            justRun { applicationEventPublisher.publishEvent(any<CommentCreatedEvent>()) }
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
                applicationEventPublisher.publishEvent(
                    CommentCreatedEvent(testUserId, testContentId)
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

            every { commentRepository.findById(parentCommentId) } returns Mono.empty()

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
    @DisplayName("getComments - 댓글 목록 페이징 조회")
    inner class GetComments {

        @Test
        @DisplayName("콘텐츠의 댓글을 페이징 조회하면, CommentListResponse를 반환한다")
        fun getComments_WithPagination_ReturnsCommentListResponse() {
            // Given
            val comment1 = Comment(
                id = testCommentId,
                contentId = testContentId,
                userId = testUserId,
                content = "Comment 1",
                parentCommentId = null,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId.toString()
            )

            val comment2Id = UUID.randomUUID()
            val comment2 = Comment(
                id = comment2Id,
                contentId = testContentId,
                userId = testUserId,
                content = "Comment 2",
                parentCommentId = null,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId.toString()
            )

            val userInfoMap = mapOf(
                testUserId to UserInfo("TestUser", "https://example.com/profile.jpg")
            )

            // limit=2로 요청하지만, limit+1=3개를 조회하여 hasNext 확인
            every { commentRepository.findTopLevelCommentsByContentId(testContentId, null, 2) } returns Flux.fromIterable(listOf(
                comment1,
                comment2,
                Comment(
                    id = UUID.randomUUID(),
                    contentId = testContentId,
                    userId = testUserId,
                    content = "Comment 3",
                    parentCommentId = null,
                    createdAt = LocalDateTime.now(),
                    createdBy = testUserId.toString(),
                    updatedAt = LocalDateTime.now(),
                    updatedBy = testUserId.toString()
                )
            ))
            every { userProfileRepository.findUserInfosByUserIds(setOf(testUserId)) } returns userInfoMap
            every { commentRepository.countRepliesByParentCommentIds(any()) } returns Mono.just(emptyMap())
            every { commentLikeRepository.countByCommentIds(any()) } returns Mono.just(emptyMap())
            every { commentLikeRepository.findLikedCommentIds(any(), any()) } returns Mono.just(emptySet())

            // When
            val result = commentService.getComments(null, testContentId, null, 2)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(2, response.comments.size)
                    assertEquals(true, response.hasNext)
                    assertEquals(comment2Id.toString(), response.nextCursor)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("대댓글은 프론트엔드 요청 시에만 로드되도록 빈 리스트가 반환된다")
        fun getComments_DoesNotPreloadReplies() {
            // Given
            val parentComment = Comment(
                id = testCommentId,
                contentId = testContentId,
                userId = testUserId,
                content = "Parent comment",
                parentCommentId = null,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId.toString()
            )

            val userInfoMap = mapOf(
                testUserId to UserInfo("TestUser", "https://example.com/profile.jpg")
            )

            every { commentRepository.findTopLevelCommentsByContentId(testContentId, null, 20) } returns Flux.fromIterable(listOf(
                parentComment
            ))
            every { userProfileRepository.findUserInfosByUserIds(setOf(testUserId)) } returns userInfoMap
            every { commentRepository.countRepliesByParentCommentIds(listOf(testCommentId)) } returns Mono.just(mapOf(testCommentId to 5))
            every { commentLikeRepository.countByCommentIds(any()) } returns Mono.just(emptyMap())
            every { commentLikeRepository.findLikedCommentIds(any(), any()) } returns Mono.just(emptySet())

            // When
            val result = commentService.getComments(null, testContentId, null, 20)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(1, response.comments.size)
                    assertEquals(5, response.comments[0].replyCount) // 개수만 표시
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getReplies - 대댓글 목록 페이징 조회")
    inner class GetReplies {

        @Test
        @DisplayName("부모 댓글의 대댓글을 페이징 조회하면, CommentListResponse를 반환한다")
        fun getReplies_WithPagination_ReturnsCommentListResponse() {
            // Given
            val parentCommentId = UUID.randomUUID()
            val reply1Id = UUID.randomUUID()
            val reply2Id = UUID.randomUUID()

            val reply1 = Comment(
                id = reply1Id,
                contentId = testContentId,
                userId = testUserId,
                content = "Reply 1",
                parentCommentId = parentCommentId,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId.toString()
            )

            val reply2 = Comment(
                id = reply2Id,
                contentId = testContentId,
                userId = testUserId,
                content = "Reply 2",
                parentCommentId = parentCommentId,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId.toString()
            )

            val userInfoMap = mapOf(
                testUserId to UserInfo("TestUser", "https://example.com/profile.jpg")
            )

            every { commentRepository.findRepliesByParentCommentId(parentCommentId, null, 2) } returns Flux.fromIterable(listOf(
                reply1,
                reply2,
                Comment(
                    id = UUID.randomUUID(),
                    contentId = testContentId,
                    userId = testUserId,
                    content = "Reply 3",
                    parentCommentId = parentCommentId,
                    createdAt = LocalDateTime.now(),
                    createdBy = testUserId.toString(),
                    updatedAt = LocalDateTime.now(),
                    updatedBy = testUserId.toString()
                )
            ))
            every { userProfileRepository.findUserInfosByUserIds(setOf(testUserId)) } returns userInfoMap
            every { commentLikeRepository.countByCommentIds(any()) } returns Mono.just(emptyMap())
            every { commentLikeRepository.findLikedCommentIds(any(), any()) } returns Mono.just(emptySet())

            // When
            val result = commentService.getReplies(null, parentCommentId, null, 2)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(2, response.comments.size)
                    assertEquals(true, response.hasNext)
                    assertEquals(reply2Id.toString(), response.nextCursor)
                    assertEquals("Reply 1", response.comments[0].content)
                    assertEquals("Reply 2", response.comments[1].content)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("더 이상 조회할 대댓글이 없으면, hasNext가 false이다")
        fun getReplies_NoMoreReplies_HasNextIsFalse() {
            // Given
            val parentCommentId = UUID.randomUUID()
            val reply = Comment(
                id = UUID.randomUUID(),
                contentId = testContentId,
                userId = testUserId,
                content = "Last reply",
                parentCommentId = parentCommentId,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId.toString()
            )

            val userInfoMap = mapOf(
                testUserId to UserInfo("TestUser", "https://example.com/profile.jpg")
            )

            // limit=20인데 1개만 조회됨 -> hasNext = false
            every { commentRepository.findRepliesByParentCommentId(parentCommentId, null, 20) } returns Flux.fromIterable(listOf(reply))
            every { userProfileRepository.findUserInfosByUserIds(setOf(testUserId)) } returns userInfoMap
            every { commentLikeRepository.countByCommentIds(any()) } returns Mono.just(emptyMap())
            every { commentLikeRepository.findLikedCommentIds(any(), any()) } returns Mono.just(emptySet())

            // When
            val result = commentService.getReplies(null, parentCommentId, null, 20)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(1, response.comments.size)
                    assertEquals(false, response.hasNext)
                    assertNull(response.nextCursor)
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("deleteComment - 댓글 삭제")
    inner class DeleteComment {

        @Test
        @DisplayName("댓글을 삭제하면, Repository에서 삭제하고 CommentDeletedEvent를 발행한다")
        fun deleteComment_Success() {
            // Given
            val comment = Comment(
                id = testCommentId,
                contentId = testContentId,
                userId = testUserId,
                content = "Test comment",
                parentCommentId = null,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId.toString()
            )

            every { commentRepository.findById(testCommentId) } returns Mono.just(comment)
            every { commentRepository.delete(testCommentId, testUserId) } returns Mono.empty()
            justRun { applicationEventPublisher.publishEvent(any<CommentDeletedEvent>()) }

            // When
            val result = commentService.deleteComment(testUserId, testCommentId)

            // Then
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { commentRepository.delete(testCommentId, testUserId) }
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(
                    CommentDeletedEvent(testUserId, testContentId)
                )
            }
        }

        @Test
        @DisplayName("댓글이 존재하지 않으면, CommentNotFoundException을 발생시킨다")
        fun deleteComment_NotFound_ThrowsException() {
            // Given
            every { commentRepository.findById(testCommentId) } returns Mono.empty()

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
                createdBy = otherUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = otherUserId.toString()
            )

            every { commentRepository.findById(testCommentId) } returns Mono.just(comment)

            // When & Then
            StepVerifier.create(commentService.deleteComment(testUserId, testCommentId))
                .expectError(CommentException.CommentAccessDeniedException::class.java)
                .verify()
        }
    }
}
