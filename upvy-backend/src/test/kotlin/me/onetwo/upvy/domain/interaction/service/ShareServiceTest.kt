package me.onetwo.upvy.domain.interaction.service

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import me.onetwo.upvy.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.upvy.domain.analytics.service.ContentInteractionService
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.infrastructure.event.ReactiveEventPublisher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

/**
 * 공유 서비스 단위 테스트
 *
 * Repository의 동작을 모킹하여 Service 계층의 비즈니스 로직만 검증합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("ShareService 단위 테스트")
class ShareServiceTest : BaseReactiveTest {

    @MockK
    private lateinit var contentInteractionService: ContentInteractionService

    @MockK
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @MockK
    private lateinit var eventPublisher: ReactiveEventPublisher

    private lateinit var shareService: ShareServiceImpl

    private val testUserId = UUID.randomUUID()
    private val testContentId = UUID.randomUUID()
    private val shareBaseUrl = "https://api.upvy.org"

    @BeforeEach
    fun setUp() {
        shareService = ShareServiceImpl(
            contentInteractionService = contentInteractionService,
            contentInteractionRepository = contentInteractionRepository,
            eventPublisher = eventPublisher,
            shareBaseUrl = shareBaseUrl
        )
    }

    @Nested
    @DisplayName("shareContent - 콘텐츠 공유")
    inner class ShareContent {

        @Test
        @DisplayName("콘텐츠를 공유하면, 카운트를 증가시키고 이벤트를 발행한 후 공유 수를 반환한다")
        fun shareContent_Success() {
            // Given
            every { contentInteractionService.incrementShareCount(testContentId) } returns Mono.empty()
            justRun { eventPublisher.publish(any()) }
            every { contentInteractionRepository.getShareCount(testContentId) } returns Mono.just(5)

            // When
            val result = shareService.shareContent(testUserId, testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(5, response.shareCount)
                }
                .verifyComplete()

            verify(exactly = 1) { contentInteractionService.incrementShareCount(testContentId) }
            verify(exactly = 1) { eventPublisher.publish(any()) }
            verify(exactly = 1) { contentInteractionRepository.getShareCount(testContentId) }
        }
    }

    @Nested
    @DisplayName("getShareLink - 공유 링크 생성")
    inner class GetShareLink {

        @Test
        @DisplayName("공유 링크를 생성하면, 콘텐츠 ID가 포함된 URL을 반환한다")
        fun getShareLink_Success() {
            // When
            val result = shareService.getShareLink(testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals("https://api.upvy.org/watch/$testContentId", response.shareUrl)
                }
                .verifyComplete()
        }
    }
}
