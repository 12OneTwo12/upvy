package me.onetwo.growsnap.domain.interaction.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import me.onetwo.growsnap.domain.interaction.model.CommentLike
import me.onetwo.growsnap.domain.interaction.repository.CommentLikeRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.test.StepVerifier
import java.time.LocalDateTime
import java.util.UUID

/**
 * 댓글 좋아요 서비스 단위 테스트
 *
 * Repository의 동작을 모킹하여 Service 계층의 비즈니스 로직만 검증합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("CommentLikeService 단위 테스트")
class CommentLikeServiceTest {

    @MockK
    private lateinit var commentLikeRepository: CommentLikeRepository

    @InjectMockKs
    private lateinit var commentLikeService: CommentLikeServiceImpl

    private val testUserId = UUID.randomUUID()
    private val testCommentId = UUID.randomUUID()

    @Nested
    @DisplayName("likeComment - 댓글 좋아요")
    inner class LikeComment {

        @Test
        @DisplayName("새로운 댓글 좋아요를 생성하면, Repository에 저장하고 좋아요 응답을 반환한다")
        fun likeComment_New_Success() {
            // Given
            val commentLike = CommentLike(
                id = 1L,
                userId = testUserId,
                commentId = testCommentId,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId,
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId
            )

            every { commentLikeRepository.exists(testUserId, testCommentId) } returns false
            every { commentLikeRepository.save(testUserId, testCommentId) } returns commentLike
            every { commentLikeRepository.countByCommentId(testCommentId) } returns 1

            // When
            val result = commentLikeService.likeComment(testUserId, testCommentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testCommentId.toString(), response.commentId)
                    assertEquals(1, response.likeCount)
                    assertEquals(true, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 1) { commentLikeRepository.save(testUserId, testCommentId) }
        }

        @Test
        @DisplayName("이미 댓글 좋아요가 있으면, 중복 생성하지 않는다 (idempotent)")
        fun likeComment_AlreadyExists_Idempotent() {
            // Given
            every { commentLikeRepository.exists(testUserId, testCommentId) } returns true
            every { commentLikeRepository.countByCommentId(testCommentId) } returns 1

            // When
            val result = commentLikeService.likeComment(testUserId, testCommentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testCommentId.toString(), response.commentId)
                    assertEquals(1, response.likeCount)
                    assertEquals(true, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 0) { commentLikeRepository.save(any(), any()) }
        }
    }

    @Nested
    @DisplayName("unlikeComment - 댓글 좋아요 취소")
    inner class UnlikeComment {

        @Test
        @DisplayName("댓글 좋아요를 취소하면, Repository에서 삭제하고 좋아요 응답을 반환한다")
        fun unlikeComment_Success() {
            // Given
            every { commentLikeRepository.exists(testUserId, testCommentId) } returns true
            every { commentLikeRepository.delete(testUserId, testCommentId) } returns Unit
            every { commentLikeRepository.countByCommentId(testCommentId) } returns 0

            // When
            val result = commentLikeService.unlikeComment(testUserId, testCommentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testCommentId.toString(), response.commentId)
                    assertEquals(0, response.likeCount)
                    assertEquals(false, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 1) { commentLikeRepository.delete(testUserId, testCommentId) }
        }

        @Test
        @DisplayName("댓글 좋아요가 없으면, 삭제하지 않는다 (idempotent)")
        fun unlikeComment_NotExists_Idempotent() {
            // Given
            every { commentLikeRepository.exists(testUserId, testCommentId) } returns false
            every { commentLikeRepository.countByCommentId(testCommentId) } returns 0

            // When
            val result = commentLikeService.unlikeComment(testUserId, testCommentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testCommentId.toString(), response.commentId)
                    assertEquals(0, response.likeCount)
                    assertEquals(false, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 0) { commentLikeRepository.delete(any(), any()) }
        }
    }

    @Nested
    @DisplayName("getLikeCount - 댓글 좋아요 수 조회")
    inner class GetLikeCount {

        @Test
        @DisplayName("댓글 좋아요 수를 조회하면, Repository에서 가져온다")
        fun getLikeCount_Success() {
            // Given
            every { commentLikeRepository.countByCommentId(testCommentId) } returns 42

            // When
            val result = commentLikeService.getLikeCount(testCommentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testCommentId.toString(), response.commentId)
                    assertEquals(42, response.likeCount)
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getLikeStatus - 댓글 좋아요 상태 조회")
    inner class GetLikeStatus {

        @Test
        @DisplayName("댓글 좋아요 상태 조회 시, Repository의 exists 결과를 반환한다 (좋아요O)")
        fun getLikeStatus_WhenLiked_ReturnsTrue() {
            // Given: 사용자가 댓글에 좋아요를 누른 상태
            every { commentLikeRepository.exists(testUserId, testCommentId) } returns true

            // When: 좋아요 상태 조회
            val result = commentLikeService.getLikeStatus(testUserId, testCommentId)

            // Then: isLiked = true 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testCommentId.toString(), response.commentId)
                    assertEquals(true, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 1) { commentLikeRepository.exists(testUserId, testCommentId) }
        }

        @Test
        @DisplayName("댓글 좋아요 상태 조회 시, Repository의 exists 결과를 반환한다 (좋아요X)")
        fun getLikeStatus_WhenNotLiked_ReturnsFalse() {
            // Given: 사용자가 댓글에 좋아요를 누르지 않은 상태
            every { commentLikeRepository.exists(testUserId, testCommentId) } returns false

            // When: 좋아요 상태 조회
            val result = commentLikeService.getLikeStatus(testUserId, testCommentId)

            // Then: isLiked = false 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testCommentId.toString(), response.commentId)
                    assertEquals(false, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 1) { commentLikeRepository.exists(testUserId, testCommentId) }
        }
    }
}
