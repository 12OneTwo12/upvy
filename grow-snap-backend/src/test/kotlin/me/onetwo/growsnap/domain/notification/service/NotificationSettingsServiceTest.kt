package me.onetwo.growsnap.domain.notification.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.growsnap.domain.notification.dto.UpdateNotificationSettingsRequest
import me.onetwo.growsnap.domain.notification.model.NotificationSettings
import me.onetwo.growsnap.domain.notification.repository.NotificationSettingsRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * NotificationSettingsService 단위 테스트
 */
@DisplayName("알림 설정 Service 테스트")
class NotificationSettingsServiceTest {

    private lateinit var notificationSettingsRepository: NotificationSettingsRepository
    private lateinit var notificationSettingsService: NotificationSettingsService

    private val testUserId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        notificationSettingsRepository = mockk()
        notificationSettingsService = NotificationSettingsServiceImpl(notificationSettingsRepository)
    }

    private fun createTestSettings(
        userId: UUID = testUserId,
        allNotificationsEnabled: Boolean = true,
        likeNotificationsEnabled: Boolean = true,
        commentNotificationsEnabled: Boolean = true,
        followNotificationsEnabled: Boolean = true
    ): NotificationSettings {
        return NotificationSettings(
            id = 1L,
            userId = userId,
            allNotificationsEnabled = allNotificationsEnabled,
            likeNotificationsEnabled = likeNotificationsEnabled,
            commentNotificationsEnabled = commentNotificationsEnabled,
            followNotificationsEnabled = followNotificationsEnabled,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    @Nested
    @DisplayName("getSettings 테스트")
    inner class GetSettingsTest {

        @Test
        @DisplayName("알림 설정이 존재하면 해당 설정을 반환한다")
        fun returnsExistingSettings() {
            // Given
            val existingSettings = createTestSettings()
            every { notificationSettingsRepository.findByUserId(testUserId) } returns Mono.just(existingSettings)

            // When
            val result = notificationSettingsService.getSettings(testUserId)

            // Then
            StepVerifier.create(result)
                .expectNext(existingSettings)
                .verifyComplete()

            verify(exactly = 1) { notificationSettingsRepository.findByUserId(testUserId) }
        }

        @Test
        @DisplayName("알림 설정이 없으면 기본 설정을 생성하고 반환한다")
        fun createsDefaultSettingsIfNotExists() {
            // Given
            val defaultSettings = createTestSettings()
            every { notificationSettingsRepository.findByUserId(testUserId) } returns Mono.empty()
            every { notificationSettingsRepository.save(any()) } returns Mono.just(defaultSettings)

            // When
            val result = notificationSettingsService.getSettings(testUserId)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { settings ->
                    settings.allNotificationsEnabled &&
                        settings.likeNotificationsEnabled &&
                        settings.commentNotificationsEnabled &&
                        settings.followNotificationsEnabled
                }
                .verifyComplete()

            verify(exactly = 1) { notificationSettingsRepository.findByUserId(testUserId) }
            verify(exactly = 1) { notificationSettingsRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("updateSettings 테스트")
    inner class UpdateSettingsTest {

        @Test
        @DisplayName("전체 필드를 업데이트한다")
        fun updatesAllFields() {
            // Given
            val existingSettings = createTestSettings()
            val updatedSettings = createTestSettings(
                allNotificationsEnabled = false,
                likeNotificationsEnabled = false,
                commentNotificationsEnabled = false,
                followNotificationsEnabled = false
            )

            every { notificationSettingsRepository.findByUserId(testUserId) } returns Mono.just(existingSettings)
            every { notificationSettingsRepository.update(any()) } returns Mono.just(updatedSettings)

            // When
            val request = UpdateNotificationSettingsRequest(
                allNotificationsEnabled = false,
                likeNotificationsEnabled = false,
                commentNotificationsEnabled = false,
                followNotificationsEnabled = false
            )
            val result = notificationSettingsService.updateSettings(testUserId, request)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { settings ->
                    !settings.allNotificationsEnabled &&
                        !settings.likeNotificationsEnabled &&
                        !settings.commentNotificationsEnabled &&
                        !settings.followNotificationsEnabled
                }
                .verifyComplete()

            verify(exactly = 1) { notificationSettingsRepository.findByUserId(testUserId) }
            verify(exactly = 1) { notificationSettingsRepository.update(any()) }
        }

        @Test
        @DisplayName("null인 필드는 기존 값을 유지한다")
        fun preservesExistingValuesForNullFields() {
            // Given
            val existingSettings = createTestSettings()
            val updatedSettings = createTestSettings(
                allNotificationsEnabled = false,
                likeNotificationsEnabled = true,
                commentNotificationsEnabled = true,
                followNotificationsEnabled = true
            )

            every { notificationSettingsRepository.findByUserId(testUserId) } returns Mono.just(existingSettings)
            every { notificationSettingsRepository.update(any()) } returns Mono.just(updatedSettings)

            // When
            val request = UpdateNotificationSettingsRequest(
                allNotificationsEnabled = false,
                likeNotificationsEnabled = null,
                commentNotificationsEnabled = null,
                followNotificationsEnabled = null
            )
            val result = notificationSettingsService.updateSettings(testUserId, request)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { settings ->
                    !settings.allNotificationsEnabled &&
                        settings.likeNotificationsEnabled &&
                        settings.commentNotificationsEnabled &&
                        settings.followNotificationsEnabled
                }
                .verifyComplete()

            verify(exactly = 1) {
                notificationSettingsRepository.update(match { settings ->
                    !settings.allNotificationsEnabled &&
                        settings.likeNotificationsEnabled &&
                        settings.commentNotificationsEnabled &&
                        settings.followNotificationsEnabled
                })
            }
        }

        @Test
        @DisplayName("설정이 없으면 기본 설정을 생성한 후 업데이트한다")
        fun createsDefaultThenUpdatesIfNotExists() {
            // Given
            val defaultSettings = createTestSettings()
            val updatedSettings = createTestSettings(
                allNotificationsEnabled = false
            )

            every { notificationSettingsRepository.findByUserId(testUserId) } returns Mono.empty() andThen Mono.just(defaultSettings)
            every { notificationSettingsRepository.save(any()) } returns Mono.just(defaultSettings)
            every { notificationSettingsRepository.update(any()) } returns Mono.just(updatedSettings)

            // When
            val request = UpdateNotificationSettingsRequest(
                allNotificationsEnabled = false,
                likeNotificationsEnabled = null,
                commentNotificationsEnabled = null,
                followNotificationsEnabled = null
            )
            val result = notificationSettingsService.updateSettings(testUserId, request)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { settings -> !settings.allNotificationsEnabled }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("createDefaultSettings 테스트")
    inner class CreateDefaultSettingsTest {

        @Test
        @DisplayName("기본 알림 설정을 생성한다")
        fun createsDefaultSettings() {
            // Given
            val defaultSettings = createTestSettings()
            every { notificationSettingsRepository.save(any()) } returns Mono.just(defaultSettings)

            // When
            val result = notificationSettingsService.createDefaultSettings(testUserId)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { settings ->
                    settings.userId == testUserId &&
                        settings.allNotificationsEnabled &&
                        settings.likeNotificationsEnabled &&
                        settings.commentNotificationsEnabled &&
                        settings.followNotificationsEnabled
                }
                .verifyComplete()

            verify(exactly = 1) {
                notificationSettingsRepository.save(match { settings ->
                    settings.userId == testUserId &&
                        settings.allNotificationsEnabled &&
                        settings.likeNotificationsEnabled &&
                        settings.commentNotificationsEnabled &&
                        settings.followNotificationsEnabled
                })
            }
        }
    }
}
