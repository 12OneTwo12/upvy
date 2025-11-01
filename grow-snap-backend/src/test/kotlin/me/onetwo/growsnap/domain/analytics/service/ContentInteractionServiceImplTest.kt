package me.onetwo.growsnap.domain.analytics.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.content.model.ContentInteraction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

/**
 * ContentInteractionService 단위 테스트
 *
 * ContentInteractionServiceImpl의 비즈니스 로직을 검증합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("콘텐츠 인터랙션 Service 단위 테스트")
class ContentInteractionServiceImplTest {

    @MockK
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @InjectMockKs
    private lateinit var contentInteractionService: ContentInteractionServiceImpl

    @Test
    @DisplayName("createContentInteraction - ContentInteraction을 생성한다")
    fun createContentInteraction_CreatesContentInteraction() {
        // Given: 콘텐츠 ID와 생성자 ID
        val contentId = UUID.randomUUID()
        val creatorId = UUID.randomUUID()

        val contentInteractionSlot = slot<ContentInteraction>()

        every {
            contentInteractionRepository.create(capture(contentInteractionSlot))
        } returns Mono.empty()

        // When: createContentInteraction 호출
        val result = contentInteractionService.createContentInteraction(contentId, creatorId)

        // Then: repository.create가 호출되었는지 확인
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 1) { contentInteractionRepository.create(any()) }

        // 생성된 ContentInteraction 검증
        val capturedInteraction = contentInteractionSlot.captured
        assertEquals(contentId, capturedInteraction.contentId)
        assertEquals(creatorId, capturedInteraction.createdBy)
        assertEquals(creatorId, capturedInteraction.updatedBy)
        assertEquals(0, capturedInteraction.likeCount)
        assertEquals(0, capturedInteraction.commentCount)
        assertEquals(0, capturedInteraction.saveCount)
        assertEquals(0, capturedInteraction.shareCount)
        assertEquals(0, capturedInteraction.viewCount)
    }

    @Test
    @DisplayName("createContentInteraction - 모든 카운터가 0으로 초기화된다")
    fun createContentInteraction_InitializesCountersToZero() {
        // Given
        val contentId = UUID.randomUUID()
        val creatorId = UUID.randomUUID()

        val contentInteractionSlot = slot<ContentInteraction>()

        every {
            contentInteractionRepository.create(capture(contentInteractionSlot))
        } returns Mono.empty()

        // When
        contentInteractionService.createContentInteraction(contentId, creatorId).block()

        // Then: 모든 카운터가 0인지 확인
        val capturedInteraction = contentInteractionSlot.captured
        assertEquals(0, capturedInteraction.likeCount)
        assertEquals(0, capturedInteraction.commentCount)
        assertEquals(0, capturedInteraction.saveCount)
        assertEquals(0, capturedInteraction.shareCount)
        assertEquals(0, capturedInteraction.viewCount)
    }
}
