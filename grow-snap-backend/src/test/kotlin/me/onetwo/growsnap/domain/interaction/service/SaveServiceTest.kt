package me.onetwo.growsnap.domain.interaction.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.content.repository.ContentMetadataRepository
import me.onetwo.growsnap.domain.interaction.event.SaveCreatedEvent
import me.onetwo.growsnap.domain.interaction.event.SaveDeletedEvent
import me.onetwo.growsnap.domain.interaction.model.UserSave
import me.onetwo.growsnap.domain.interaction.repository.UserSaveRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime
import java.util.UUID

/**
 * 저장 서비스 단위 테스트
 *
 * Repository의 동작을 모킹하여 Service 계층의 비즈니스 로직만 검증합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("SaveService 단위 테스트")
class SaveServiceTest {

    @MockK
    private lateinit var userSaveRepository: UserSaveRepository

    @MockK
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockK
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @MockK
    private lateinit var contentMetadataRepository: ContentMetadataRepository

    @InjectMockKs
    private lateinit var saveService: SaveServiceImpl

    private val testUserId = UUID.randomUUID()
    private val testContentId = UUID.randomUUID()

    @Nested
    @DisplayName("saveContent - 콘텐츠 저장")
    inner class SaveContent {

        @Test
        @DisplayName("새로운 저장을 생성하면, Repository에 저장하고 SaveCreatedEvent를 발행한다")
        fun saveContent_New_Success() {
            // Given
            val userSave = UserSave(
                id = 1L,
                userId = testUserId,
                contentId = testContentId,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId.toString()
            )

            every { userSaveRepository.exists(testUserId, testContentId) } returns false
            every { userSaveRepository.save(testUserId, testContentId) } returns userSave
            justRun { applicationEventPublisher.publishEvent(any<SaveCreatedEvent>()) }
            every { contentInteractionRepository.getSaveCount(testContentId) } returns Mono.just(1)

            // When
            val result = saveService.saveContent(testUserId, testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(1, response.saveCount)
                    assertEquals(true, response.isSaved)
                }
                .verifyComplete()

            verify(exactly = 1) { userSaveRepository.save(testUserId, testContentId) }
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(
                    SaveCreatedEvent(testUserId, testContentId)
                )
            }
        }

        @Test
        @DisplayName("이미 저장이 있으면, 중복 생성하지 않는다 (idempotent)")
        fun saveContent_AlreadyExists_Idempotent() {
            // Given
            every { userSaveRepository.exists(testUserId, testContentId) } returns true
            every { contentInteractionRepository.getSaveCount(testContentId) } returns Mono.just(1)

            // When
            val result = saveService.saveContent(testUserId, testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(1, response.saveCount)
                    assertEquals(true, response.isSaved)
                }
                .verifyComplete()

            verify(exactly = 0) { userSaveRepository.save(any(), any()) }
            verify(exactly = 0) { applicationEventPublisher.publishEvent(any<SaveCreatedEvent>()) }
        }
    }

    @Nested
    @DisplayName("unsaveContent - 저장 취소")
    inner class UnsaveContent {

        @Test
        @DisplayName("저장을 취소하면, Repository에서 삭제하고 SaveDeletedEvent를 발행한다")
        fun unsaveContent_Success() {
            // Given
            every { userSaveRepository.exists(testUserId, testContentId) } returns true
            every { userSaveRepository.delete(testUserId, testContentId) } returns Unit
            justRun { applicationEventPublisher.publishEvent(any<SaveDeletedEvent>()) }
            every { contentInteractionRepository.getSaveCount(testContentId) } returns Mono.just(0)

            // When
            val result = saveService.unsaveContent(testUserId, testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(0, response.saveCount)
                    assertEquals(false, response.isSaved)
                }
                .verifyComplete()

            verify(exactly = 1) { userSaveRepository.delete(testUserId, testContentId) }
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(
                    SaveDeletedEvent(testUserId, testContentId)
                )
            }
        }

        @Test
        @DisplayName("저장이 없으면, 삭제하지 않는다 (idempotent)")
        fun unsaveContent_NotExists_Idempotent() {
            // Given
            every { userSaveRepository.exists(testUserId, testContentId) } returns false
            every { contentInteractionRepository.getSaveCount(testContentId) } returns Mono.just(0)

            // When
            val result = saveService.unsaveContent(testUserId, testContentId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(0, response.saveCount)
                    assertEquals(false, response.isSaved)
                }
                .verifyComplete()

            verify(exactly = 0) { userSaveRepository.delete(any(), any()) }
            verify(exactly = 0) { applicationEventPublisher.publishEvent(any<SaveDeletedEvent>()) }
        }
    }

    @Nested
    @DisplayName("getSavedContents - 저장된 콘텐츠 목록 조회")
    inner class GetSavedContents {

        @Test
        @DisplayName("저장된 콘텐츠 목록을 조회하면, N+1 없이 일괄 조회한다")
        fun getSavedContents_NoPlusOne_Success() {
            // Given
            val contentId1 = UUID.randomUUID()
            val contentId2 = UUID.randomUUID()

            val userSave1 = UserSave(
                id = 1L,
                userId = testUserId,
                contentId = contentId1,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId.toString()
            )

            val userSave2 = UserSave(
                id = 2L,
                userId = testUserId,
                contentId = contentId2,
                createdAt = LocalDateTime.now(),
                createdBy = testUserId.toString(),
                updatedAt = LocalDateTime.now(),
                updatedBy = testUserId.toString()
            )

            val contentInfoMap = mapOf(
                contentId1 to Pair("Content 1", "https://example.com/thumb1.jpg"),
                contentId2 to Pair("Content 2", "https://example.com/thumb2.jpg")
            )

            every { userSaveRepository.findByUserId(testUserId) } returns listOf(userSave1, userSave2)
            every { contentMetadataRepository.findContentInfosByContentIds(setOf(contentId1, contentId2)) } returns contentInfoMap

            // When
            val result = saveService.getSavedContents(testUserId)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(contentId1.toString(), response.contentId)
                    assertEquals("Content 1", response.title)
                    assertEquals("https://example.com/thumb1.jpg", response.thumbnailUrl)
                }
                .assertNext { response ->
                    assertEquals(contentId2.toString(), response.contentId)
                    assertEquals("Content 2", response.title)
                    assertEquals("https://example.com/thumb2.jpg", response.thumbnailUrl)
                }
                .verifyComplete()

            // N+1 방지: ContentMetadataRepository가 한 번만 호출됨
            verify(exactly = 1) { contentMetadataRepository.findContentInfosByContentIds(any()) }
        }

        @Test
        @DisplayName("저장된 콘텐츠가 없으면, 빈 Flux를 반환한다")
        fun getSavedContents_Empty_ReturnsEmptyFlux() {
            // Given
            every { userSaveRepository.findByUserId(testUserId) } returns emptyList()

            // When
            val result = saveService.getSavedContents(testUserId)

            // Then
            StepVerifier.create(result)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getSaveStatus - 저장 상태 조회")
    inner class GetSaveStatus {

        @Test
        @DisplayName("저장 상태 조회 시, Repository의 exists 결과를 반환한다 (저장O)")
        fun getSaveStatus_WhenSaved_ReturnsTrue() {
            // Given: 사용자가 콘텐츠를 저장한 상태
            every { userSaveRepository.exists(testUserId, testContentId) } returns true

            // When: 저장 상태 조회
            val result = saveService.getSaveStatus(testUserId, testContentId)

            // Then: isSaved = true 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(true, response.isSaved)
                }
                .verifyComplete()

            verify(exactly = 1) { userSaveRepository.exists(testUserId, testContentId) }
        }

        @Test
        @DisplayName("저장 상태 조회 시, Repository의 exists 결과를 반환한다 (저장X)")
        fun getSaveStatus_WhenNotSaved_ReturnsFalse() {
            // Given: 사용자가 콘텐츠를 저장하지 않은 상태
            every { userSaveRepository.exists(testUserId, testContentId) } returns false

            // When: 저장 상태 조회
            val result = saveService.getSaveStatus(testUserId, testContentId)

            // Then: isSaved = false 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(testContentId.toString(), response.contentId)
                    assertEquals(false, response.isSaved)
                }
                .verifyComplete()

            verify(exactly = 1) { userSaveRepository.exists(testUserId, testContentId) }
        }
    }
}
