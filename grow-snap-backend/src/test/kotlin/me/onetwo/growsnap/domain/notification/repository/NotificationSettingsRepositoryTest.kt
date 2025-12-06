package me.onetwo.growsnap.domain.notification.repository

import me.onetwo.growsnap.domain.notification.model.NotificationSettings
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.infrastructure.config.AbstractIntegrationTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.util.UUID

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@DisplayName("알림 설정 Repository 통합 테스트")
class NotificationSettingsRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var notificationSettingsRepository: NotificationSettingsRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private fun createTestUser(): User {
        val user = User(
            email = "test-${UUID.randomUUID()}@example.com",
            provider = OAuthProvider.GOOGLE,
            providerId = UUID.randomUUID().toString()
        )
        return userRepository.save(user).block()!!
    }

    @Nested
    @DisplayName("save 테스트")
    inner class SaveTest {

        @Test
        @DisplayName("알림 설정을 저장하고 ID를 반환한다")
        fun savesSettingsAndReturnsWithId() {
            // Given
            val user = createTestUser()
            val settings = NotificationSettings.createDefault(user.id!!)

            // When
            val result = notificationSettingsRepository.save(settings)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.id != null &&
                        saved.userId == user.id &&
                        saved.allNotificationsEnabled &&
                        saved.likeNotificationsEnabled &&
                        saved.commentNotificationsEnabled &&
                        saved.followNotificationsEnabled
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("모든 알림이 비활성화된 상태로 저장할 수 있다")
        fun savesSettingsWithAllDisabled() {
            // Given
            val user = createTestUser()
            val settings = NotificationSettings(
                userId = user.id!!,
                allNotificationsEnabled = false,
                likeNotificationsEnabled = false,
                commentNotificationsEnabled = false,
                followNotificationsEnabled = false
            )

            // When
            val result = notificationSettingsRepository.save(settings)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.id != null &&
                        !saved.allNotificationsEnabled &&
                        !saved.likeNotificationsEnabled &&
                        !saved.commentNotificationsEnabled &&
                        !saved.followNotificationsEnabled
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findByUserId 테스트")
    inner class FindByUserIdTest {

        @Test
        @DisplayName("존재하는 사용자의 알림 설정을 반환한다")
        fun returnsSettingsForExistingUser() {
            // Given
            val user = createTestUser()
            val settings = NotificationSettings.createDefault(user.id!!)
            notificationSettingsRepository.save(settings).block()

            // When
            val result = notificationSettingsRepository.findByUserId(user.id!!)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { found ->
                    found.userId == user.id &&
                        found.allNotificationsEnabled &&
                        found.likeNotificationsEnabled
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("존재하지 않는 사용자의 경우 empty를 반환한다")
        fun returnsEmptyForNonExistingUser() {
            // Given
            val nonExistingUserId = UUID.randomUUID()

            // When
            val result = notificationSettingsRepository.findByUserId(nonExistingUserId)

            // Then
            StepVerifier.create(result)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("update 테스트")
    inner class UpdateTest {

        @Test
        @DisplayName("알림 설정을 업데이트한다")
        fun updatesSettings() {
            // Given
            val user = createTestUser()
            val settings = NotificationSettings.createDefault(user.id!!)
            val saved = notificationSettingsRepository.save(settings).block()!!

            val updatedSettings = saved.copy(
                allNotificationsEnabled = false,
                likeNotificationsEnabled = false
            )

            // When
            val result = notificationSettingsRepository.update(updatedSettings)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { updated ->
                    !updated.allNotificationsEnabled &&
                        !updated.likeNotificationsEnabled &&
                        updated.commentNotificationsEnabled &&
                        updated.followNotificationsEnabled
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("부분적으로 알림 설정을 업데이트한다")
        fun partiallyUpdatesSettings() {
            // Given
            val user = createTestUser()
            val settings = NotificationSettings.createDefault(user.id!!)
            val saved = notificationSettingsRepository.save(settings).block()!!

            val updatedSettings = saved.copy(
                followNotificationsEnabled = false
            )

            // When
            val result = notificationSettingsRepository.update(updatedSettings)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { updated ->
                    updated.allNotificationsEnabled &&
                        updated.likeNotificationsEnabled &&
                        updated.commentNotificationsEnabled &&
                        !updated.followNotificationsEnabled
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("existsByUserId 테스트")
    inner class ExistsByUserIdTest {

        @Test
        @DisplayName("알림 설정이 존재하면 true를 반환한다")
        fun returnsTrueIfExists() {
            // Given
            val user = createTestUser()
            val settings = NotificationSettings.createDefault(user.id!!)
            notificationSettingsRepository.save(settings).block()

            // When
            val result = notificationSettingsRepository.existsByUserId(user.id!!)

            // Then
            StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete()
        }

        @Test
        @DisplayName("알림 설정이 존재하지 않으면 false를 반환한다")
        fun returnsFalseIfNotExists() {
            // Given
            val nonExistingUserId = UUID.randomUUID()

            // When
            val result = notificationSettingsRepository.existsByUserId(nonExistingUserId)

            // Then
            StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete()
        }
    }
}
