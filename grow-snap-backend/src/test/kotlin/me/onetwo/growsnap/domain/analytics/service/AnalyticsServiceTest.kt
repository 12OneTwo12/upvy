package me.onetwo.growsnap.domain.analytics.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import me.onetwo.growsnap.domain.analytics.dto.InteractionEventRequest
import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.dto.ViewEventRequest
import me.onetwo.growsnap.domain.analytics.event.UserInteractionEvent
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.analytics.repository.UserViewHistoryRepository
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Analytics 서비스 테스트
 *
 * 사용자 행동 추적 비즈니스 로직을 검증합니다.
 * Spring Event 패턴을 사용하여 비동기 처리되는 로직을 포함합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("Analytics Service 테스트")
class AnalyticsServiceTest {

    private lateinit var userViewHistoryRepository: UserViewHistoryRepository
    private lateinit var contentInteractionRepository: ContentInteractionRepository
    private lateinit var eventPublisher: ReactiveEventPublisher
    private lateinit var analyticsService: AnalyticsService

    private val userId = UUID.randomUUID()
    private val contentId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        userViewHistoryRepository = mockk()
        contentInteractionRepository = mockk()
        eventPublisher = mockk(relaxed = true)
        analyticsService = AnalyticsServiceImpl(
            userViewHistoryRepository,
            contentInteractionRepository,
            eventPublisher
        )
    }

    @Nested
    @DisplayName("trackViewEvent - 시청 이벤트 추적")
    inner class TrackViewEvent {

        @Test
        @DisplayName("정상 시청 이벤트 기록 시, 시청 기록을 저장하고 view_count를 증가시킨다")
        fun trackViewEvent_WithNormalView_SavesHistoryAndIncrementsViewCount() {
            // Given: 정상 시청 이벤트 (스킵 아님)
            val request = ViewEventRequest(
                contentId = contentId,
                watchedDuration = 120,
                completionRate = 85,
                skipped = false
            )

            every {
                userViewHistoryRepository.save(userId, contentId, request.watchedDuration!!, request.completionRate!!)
            } returns Mono.empty()

            every {
                contentInteractionRepository.incrementViewCount(contentId)
            } returns Mono.empty()

            // When: 시청 이벤트 추적
            val result = analyticsService.trackViewEvent(userId, request)

            // Then: 시청 기록 저장 및 view_count 증가
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) {
                userViewHistoryRepository.save(userId, contentId, request.watchedDuration!!, request.completionRate!!)
            }
            verify(exactly = 1) {
                contentInteractionRepository.incrementViewCount(contentId)
            }
        }

        @Test
        @DisplayName("스킵 이벤트(3초 이내) 기록 시, 시청 기록만 저장하고 view_count는 증가시키지 않는다")
        fun trackViewEvent_WithSkipEvent_SavesHistoryButDoesNotIncrementViewCount() {
            // Given: 스킵 이벤트 (3초 이내 시청)
            val request = ViewEventRequest(
                contentId = contentId,
                watchedDuration = 2,
                completionRate = 5,
                skipped = true
            )

            every {
                userViewHistoryRepository.save(userId, contentId, request.watchedDuration!!, request.completionRate!!)
            } returns Mono.empty()

            // When: 스킵 이벤트 추적
            val result = analyticsService.trackViewEvent(userId, request)

            // Then: 시청 기록만 저장, view_count 증가 없음
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) {
                userViewHistoryRepository.save(userId, contentId, request.watchedDuration!!, request.completionRate!!)
            }
            verify(exactly = 0) {
                contentInteractionRepository.incrementViewCount(any())
            }
        }

        @Test
        @DisplayName("완료율 100% 시청 시, 시청 기록을 저장하고 view_count를 증가시킨다")
        fun trackViewEvent_WithFullCompletion_SavesHistoryAndIncrementsViewCount() {
            // Given: 완료율 100% 시청
            val request = ViewEventRequest(
                contentId = contentId,
                watchedDuration = 180,
                completionRate = 100,
                skipped = false
            )

            every {
                userViewHistoryRepository.save(userId, contentId, request.watchedDuration!!, request.completionRate!!)
            } returns Mono.empty()

            every {
                contentInteractionRepository.incrementViewCount(contentId)
            } returns Mono.empty()

            // When: 시청 이벤트 추적
            val result = analyticsService.trackViewEvent(userId, request)

            // Then: 시청 기록 저장 및 view_count 증가
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) {
                userViewHistoryRepository.save(userId, contentId, request.watchedDuration!!, request.completionRate!!)
            }
            verify(exactly = 1) {
                contentInteractionRepository.incrementViewCount(contentId)
            }
        }
    }

    @Nested
    @DisplayName("trackInteractionEvent - 인터랙션 이벤트 추적 (Spring Event 패턴)")
    inner class TrackInteractionEvent {

        @Test
        @DisplayName("좋아요 이벤트 기록 시, like_count를 증가시키고 UserInteractionEvent를 발행한다")
        fun trackInteractionEvent_WithLike_IncrementsLikeCountAndPublishesEvent() {
            // Given: 좋아요 이벤트
            val request = InteractionEventRequest(
                contentId = contentId,
                interactionType = InteractionType.LIKE
            )

            val eventSlot = slot<UserInteractionEvent>()

            every {
                contentInteractionRepository.incrementLikeCount(contentId)
            } returns Mono.empty()

            every {
                eventPublisher.publish(capture(eventSlot))
            } returns Unit

            // When: 좋아요 이벤트 추적
            val result = analyticsService.trackInteractionEvent(userId, request)

            // Then: like_count 증가 및 이벤트 발행
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) {
                contentInteractionRepository.incrementLikeCount(contentId)
            }

            verify(exactly = 1) {
                eventPublisher.publish(any<UserInteractionEvent>())
            }

            assertThat(eventSlot.captured.userId).isEqualTo(userId)
            assertThat(eventSlot.captured.contentId).isEqualTo(contentId)
            assertThat(eventSlot.captured.interactionType).isEqualTo(InteractionType.LIKE)
        }

        @Test
        @DisplayName("저장 이벤트 기록 시, save_count를 증가시키고 UserInteractionEvent를 발행한다")
        fun trackInteractionEvent_WithSave_IncrementsSaveCountAndPublishesEvent() {
            // Given: 저장 이벤트
            val request = InteractionEventRequest(
                contentId = contentId,
                interactionType = InteractionType.SAVE
            )

            val eventSlot = slot<UserInteractionEvent>()

            every {
                contentInteractionRepository.incrementSaveCount(contentId)
            } returns Mono.empty()

            every {
                eventPublisher.publish(capture(eventSlot))
            } returns Unit

            // When: 저장 이벤트 추적
            val result = analyticsService.trackInteractionEvent(userId, request)

            // Then: save_count 증가 및 이벤트 발행
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) {
                contentInteractionRepository.incrementSaveCount(contentId)
            }

            verify(exactly = 1) {
                eventPublisher.publish(any<UserInteractionEvent>())
            }

            assertThat(eventSlot.captured.userId).isEqualTo(userId)
            assertThat(eventSlot.captured.contentId).isEqualTo(contentId)
            assertThat(eventSlot.captured.interactionType).isEqualTo(InteractionType.SAVE)
        }

        @Test
        @DisplayName("공유 이벤트 기록 시, share_count를 증가시키고 UserInteractionEvent를 발행한다")
        fun trackInteractionEvent_WithShare_IncrementsShareCountAndPublishesEvent() {
            // Given: 공유 이벤트
            val request = InteractionEventRequest(
                contentId = contentId,
                interactionType = InteractionType.SHARE
            )

            val eventSlot = slot<UserInteractionEvent>()

            every {
                contentInteractionRepository.incrementShareCount(contentId)
            } returns Mono.empty()

            every {
                eventPublisher.publish(capture(eventSlot))
            } returns Unit

            // When: 공유 이벤트 추적
            val result = analyticsService.trackInteractionEvent(userId, request)

            // Then: share_count 증가 및 이벤트 발행
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) {
                contentInteractionRepository.incrementShareCount(contentId)
            }

            verify(exactly = 1) {
                eventPublisher.publish(any<UserInteractionEvent>())
            }

            assertThat(eventSlot.captured.userId).isEqualTo(userId)
            assertThat(eventSlot.captured.contentId).isEqualTo(contentId)
            assertThat(eventSlot.captured.interactionType).isEqualTo(InteractionType.SHARE)
        }

        @Test
        @DisplayName("댓글 이벤트 기록 시, comment_count를 증가시키고 이벤트는 발행하지 않는다")
        fun trackInteractionEvent_WithComment_IncrementsCommentCountButDoesNotPublishEvent() {
            // Given: 댓글 이벤트
            val request = InteractionEventRequest(
                contentId = contentId,
                interactionType = InteractionType.COMMENT
            )

            every {
                contentInteractionRepository.incrementCommentCount(contentId)
            } returns Mono.empty()

            // When: 댓글 이벤트 추적
            val result = analyticsService.trackInteractionEvent(userId, request)

            // Then: comment_count 증가만, 이벤트 발행 안 함
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) {
                contentInteractionRepository.incrementCommentCount(contentId)
            }

            verify(exactly = 0) {
                eventPublisher.publish(any<UserInteractionEvent>())
            }
        }
    }
}
