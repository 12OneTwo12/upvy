package me.onetwo.upvy.domain.analytics.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.domain.analytics.dto.ViewEventRequest
import me.onetwo.upvy.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.upvy.domain.analytics.repository.UserViewHistoryRepository
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
 * 사용자 시청 기록 추적 비즈니스 로직을 검증합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("Analytics Service 테스트")
class AnalyticsServiceTest : BaseReactiveTest {

    private lateinit var userViewHistoryRepository: UserViewHistoryRepository
    private lateinit var contentInteractionRepository: ContentInteractionRepository
    private lateinit var analyticsService: AnalyticsService

    private val userId = UUID.randomUUID()
    private val contentId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        userViewHistoryRepository = mockk()
        contentInteractionRepository = mockk()
        analyticsService = AnalyticsServiceImpl(
            userViewHistoryRepository,
            contentInteractionRepository
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
}
