package me.onetwo.growsnap.domain.notification.repository

import me.onetwo.growsnap.domain.notification.model.Notification
import me.onetwo.growsnap.domain.notification.model.NotificationTargetType
import me.onetwo.growsnap.domain.notification.model.NotificationType
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

/**
 * NotificationRepository 통합 테스트
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@DisplayName("알림 Repository 통합 테스트")
class NotificationRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

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

    private fun createTestNotification(
        userId: UUID,
        type: NotificationType = NotificationType.LIKE,
        title: String = "새로운 좋아요",
        body: String = "testUser님이 게시물을 좋아합니다.",
        isRead: Boolean = false,
        actorId: UUID? = UUID.randomUUID(),
        targetType: NotificationTargetType? = NotificationTargetType.CONTENT,
        targetId: UUID? = UUID.randomUUID(),
        data: String? = """{"type": "LIKE"}"""
    ): Notification {
        return Notification(
            userId = userId,
            type = type,
            title = title,
            body = body,
            isRead = isRead,
            actorId = actorId,
            targetType = targetType,
            targetId = targetId,
            data = data
        )
    }

    @Nested
    @DisplayName("save 테스트")
    inner class SaveTest {

        @Test
        @DisplayName("알림을 저장하고 ID를 반환한다")
        fun savesNotificationAndReturnsWithId() {
            // Given
            val user = createTestUser()
            val notification = createTestNotification(user.id!!)

            // When
            val result = notificationRepository.save(notification)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.id != null &&
                        saved.userId == user.id &&
                        saved.type == NotificationType.LIKE &&
                        saved.title == "새로운 좋아요" &&
                        !saved.isRead
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("다양한 알림 유형을 저장할 수 있다")
        fun savesDifferentNotificationTypes() {
            // Given
            val user = createTestUser()
            val commentNotification = createTestNotification(
                userId = user.id!!,
                type = NotificationType.COMMENT,
                title = "새로운 댓글",
                body = "testUser님이 댓글을 남겼습니다."
            )

            // When
            val result = notificationRepository.save(commentNotification)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.type == NotificationType.COMMENT &&
                        saved.title == "새로운 댓글"
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findById 테스트")
    inner class FindByIdTest {

        @Test
        @DisplayName("알림을 ID로 조회한다")
        fun findsNotificationById() {
            // Given
            val user = createTestUser()
            val notification = createTestNotification(user.id!!)
            val saved = notificationRepository.save(notification).block()!!

            // When
            val result = notificationRepository.findById(saved.id!!)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { found ->
                    found.id == saved.id &&
                        found.userId == user.id
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("존재하지 않는 알림 ID는 empty를 반환한다")
        fun returnsEmptyForNonExistingId() {
            // Given
            val nonExistingId = 99999L

            // When
            val result = notificationRepository.findById(nonExistingId)

            // Then
            StepVerifier.create(result)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findByUserId 테스트")
    inner class FindByUserIdTest {

        @Test
        @DisplayName("사용자의 알림 목록을 커서 기반으로 조회한다")
        fun findsNotificationsWithCursor() {
            // Given
            val user = createTestUser()
            for (i in 1..5) {
                notificationRepository.save(createTestNotification(user.id!!)).block()
            }

            // When
            val result = notificationRepository.findByUserId(user.id!!, null, 3)

            // Then
            StepVerifier.create(result.collectList())
                .expectNextMatches { notifications ->
                    notifications.size == 3 &&
                        notifications.all { it.userId == user.id }
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("커서 이후의 알림만 조회한다")
        fun findsNotificationsAfterCursor() {
            // Given
            val user = createTestUser()
            val saved = mutableListOf<Notification>()
            for (i in 1..5) {
                saved.add(notificationRepository.save(createTestNotification(user.id!!)).block()!!)
            }
            val cursor = saved[2].id!! // 3번째 알림을 커서로

            // When
            val result = notificationRepository.findByUserId(user.id!!, cursor, 10)

            // Then
            StepVerifier.create(result.collectList())
                .expectNextMatches { notifications ->
                    notifications.all { it.id!! < cursor }
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("알림이 없으면 빈 결과를 반환한다")
        fun returnsEmptyIfNoNotifications() {
            // Given
            val nonExistingUserId = UUID.randomUUID()

            // When
            val result = notificationRepository.findByUserId(nonExistingUserId, null, 10)

            // Then
            StepVerifier.create(result.collectList())
                .expectNextMatches { notifications -> notifications.isEmpty() }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("countUnreadByUserId 테스트")
    inner class CountUnreadByUserIdTest {

        @Test
        @DisplayName("읽지 않은 알림 수를 반환한다")
        fun countsUnreadNotifications() {
            // Given
            val user = createTestUser()
            notificationRepository.save(createTestNotification(user.id!!, isRead = false)).block()
            notificationRepository.save(createTestNotification(user.id!!, isRead = false)).block()
            notificationRepository.save(createTestNotification(user.id!!, isRead = true)).block()

            // When
            val result = notificationRepository.countUnreadByUserId(user.id!!)

            // Then
            StepVerifier.create(result)
                .expectNext(2L)
                .verifyComplete()
        }

        @Test
        @DisplayName("알림이 없으면 0을 반환한다")
        fun returnsZeroIfNoNotifications() {
            // Given
            val nonExistingUserId = UUID.randomUUID()

            // When
            val result = notificationRepository.countUnreadByUserId(nonExistingUserId)

            // Then
            StepVerifier.create(result)
                .expectNext(0L)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("markAsRead 테스트")
    inner class MarkAsReadTest {

        @Test
        @DisplayName("알림을 읽음으로 표시한다")
        fun marksNotificationAsRead() {
            // Given
            val user = createTestUser()
            val notification = createTestNotification(user.id!!, isRead = false)
            val saved = notificationRepository.save(notification).block()!!

            // When
            val result = notificationRepository.markAsRead(saved.id!!, user.id!!)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { updated ->
                    updated.isRead
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("markAllAsRead 테스트")
    inner class MarkAllAsReadTest {

        @Test
        @DisplayName("사용자의 모든 알림을 읽음으로 표시한다")
        fun marksAllNotificationsAsRead() {
            // Given
            val user = createTestUser()
            notificationRepository.save(createTestNotification(user.id!!, isRead = false)).block()
            notificationRepository.save(createTestNotification(user.id!!, isRead = false)).block()
            notificationRepository.save(createTestNotification(user.id!!, isRead = false)).block()

            // When
            val result = notificationRepository.markAllAsRead(user.id!!)

            // Then
            StepVerifier.create(result)
                .verifyComplete()

            // Verify all are read
            StepVerifier.create(notificationRepository.countUnreadByUserId(user.id!!))
                .expectNext(0L)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("deleteById 테스트")
    inner class DeleteByIdTest {

        @Test
        @DisplayName("알림을 삭제한다 (soft delete)")
        fun deletesNotification() {
            // Given
            val user = createTestUser()
            val notification = createTestNotification(user.id!!)
            val saved = notificationRepository.save(notification).block()!!

            // When
            val result = notificationRepository.deleteById(saved.id!!, user.id!!)

            // Then
            StepVerifier.create(result)
                .verifyComplete()

            // Verify deletion (should not be found)
            StepVerifier.create(notificationRepository.findById(saved.id!!))
                .verifyComplete()
        }
    }
}
