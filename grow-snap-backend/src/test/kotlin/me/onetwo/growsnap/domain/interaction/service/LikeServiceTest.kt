package me.onetwo.growsnap.domain.interaction.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.analytics.service.ContentInteractionService
import me.onetwo.growsnap.domain.interaction.model.UserLike
import me.onetwo.growsnap.domain.interaction.repository.UserLikeRepository
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * 좋아요 서비스 단위 테스트 (Reactor Sinks API 패턴)
 *
 * Repository의 동작을 모킹하여 Service 계층의 비즈니스 로직만 검증합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("LikeService 단위 테스트")
class LikeServiceTest {

    @MockK
    private lateinit var userLikeRepository: UserLikeRepository

    @MockK
    private lateinit var contentInteractionService: ContentInteractionService

    @MockK
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @MockK
    private lateinit var eventPublisher: ReactiveEventPublisher

    @InjectMockKs
    private lateinit var likeService: LikeServiceImpl

    private val testUserId = UUID.randomUUID()
    private val testContentId = UUID.randomUUID()

    @Nested
    @DisplayName("likeContent - 좋아요")
    inner class LikeContent {

        @Test
        @DisplayName("새로운 좋아요를 생성하면, Repository에 저장하고 카운트를 증가시킨 후 UserInteractionEvent를 발행한다")
        fun likeContent_New_Success() {
            // Given
            val userLike = UserLike(
                id = 1L,
                userId = testUserId,
                contentId = testContentId,
                createdAt = Instant.now(),
                createdBy = testUserId.toString(),
                updatedAt = Instant.now(),
                updatedBy = testUserId.toString()
            )

            every { userLikeRepository.exists(testUserId, testContentId) } returns Mono.just(false)
            every { userLikeRepository.save(testUserId, testContentId) } returns Mono.just(userLike)
            every { contentInteractionService.incrementLikeCount(testContentId) } returns Mono.empty()
            justRun { eventPublisher.publish(any()) }
            every { contentInteractionRepository.getLikeCount(testContentId) } returns Mono.just(1)

            // When
            val result = likeService.likeContent(testUserId, testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(1, response.likeCount)
                    assertEquals(true, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 1) { userLikeRepository.save(testUserId, testContentId) }
            verify(exactly = 1) { contentInteractionService.incrementLikeCount(testContentId) }
            verify(exactly = 1) { eventPublisher.publish(any()) }
        }

        @Test
        @DisplayName("이미 좋아요가 있으면, 중복 생성하지 않는다 (idempotent)")
        fun likeContent_AlreadyExists_Idempotent() {
            // Given
            every { userLikeRepository.exists(testUserId, testContentId) } returns Mono.just(true)
            every { contentInteractionRepository.getLikeCount(testContentId) } returns Mono.just(1)

            // When
            val result = likeService.likeContent(testUserId, testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(1, response.likeCount)
                    assertEquals(true, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 0) { userLikeRepository.save(any(), any()) }
            verify(exactly = 0) { contentInteractionService.incrementLikeCount(any()) }
            verify(exactly = 0) { eventPublisher.publish(any()) }
        }
    }

    @Nested
    @DisplayName("unlikeContent - 좋아요 취소")
    inner class UnlikeContent {

        @Test
        @DisplayName("좋아요를 취소하면, Repository에서 삭제하고 LikeDeletedEvent를 발행한다")
        fun unlikeContent_Success() {
            // Given
            every { userLikeRepository.exists(testUserId, testContentId) } returns Mono.just(true)
            every { userLikeRepository.delete(testUserId, testContentId) } returns Mono.empty()
            every { contentInteractionService.decrementLikeCount(testContentId) } returns Mono.empty()
            every { contentInteractionRepository.getLikeCount(testContentId) } returns Mono.just(0)

            // When
            val result = likeService.unlikeContent(testUserId, testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(0, response.likeCount)
                    assertEquals(false, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 1) { userLikeRepository.delete(testUserId, testContentId) }
            verify(exactly = 1) { contentInteractionService.decrementLikeCount(testContentId) }
        }

        @Test
        @DisplayName("좋아요가 없으면, 삭제하지 않는다 (idempotent)")
        fun unlikeContent_NotExists_Idempotent() {
            // Given
            every { userLikeRepository.exists(testUserId, testContentId) } returns Mono.just(false)
            every { contentInteractionRepository.getLikeCount(testContentId) } returns Mono.just(0)

            // When
            val result = likeService.unlikeContent(testUserId, testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(0, response.likeCount)
                    assertEquals(false, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 0) { userLikeRepository.delete(any(), any()) }
            verify(exactly = 0) { contentInteractionService.decrementLikeCount(any()) }
        }
    }

    @Nested
    @DisplayName("getLikeCount - 좋아요 수 조회")
    inner class GetLikeCount {

        @Test
        @DisplayName("좋아요 수를 조회하면, ContentInteractionRepository에서 가져온다")
        fun getLikeCount_Success() {
            // Given
            every { contentInteractionRepository.getLikeCount(testContentId) } returns Mono.just(42)

            // When
            val result = likeService.getLikeCount(testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(42, response.likeCount)
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getLikeStatus - 좋아요 상태 조회")
    inner class GetLikeStatus {

        @Test
        @DisplayName("좋아요 상태 조회 시, Repository의 exists 결과를 반환한다 (좋아요O)")
        fun getLikeStatus_WhenLiked_ReturnsTrue() {
            // Given: 사용자가 좋아요를 누른 상태
            every { userLikeRepository.exists(testUserId, testContentId) } returns Mono.just(true)

            // When: 좋아요 상태 조회
            val result = likeService.getLikeStatus(testUserId, testContentId)

            // Then: isLiked = true 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(true, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 1) { userLikeRepository.exists(testUserId, testContentId) }
        }

        @Test
        @DisplayName("좋아요 상태 조회 시, Repository의 exists 결과를 반환한다 (좋아요X)")
        fun getLikeStatus_WhenNotLiked_ReturnsFalse() {
            // Given: 사용자가 좋아요를 누르지 않은 상태
            every { userLikeRepository.exists(testUserId, testContentId) } returns Mono.just(false)

            // When: 좋아요 상태 조회
            val result = likeService.getLikeStatus(testUserId, testContentId)

            // Then: isLiked = false 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(false, response.isLiked)
                }
                .verifyComplete()

            verify(exactly = 1) { userLikeRepository.exists(testUserId, testContentId) }
        }
    }
}
