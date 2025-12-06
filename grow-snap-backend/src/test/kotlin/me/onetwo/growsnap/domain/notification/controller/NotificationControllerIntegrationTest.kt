package me.onetwo.growsnap.domain.notification.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.notification.model.Notification
import me.onetwo.growsnap.domain.notification.model.NotificationTargetType
import me.onetwo.growsnap.domain.notification.model.NotificationType
import me.onetwo.growsnap.domain.notification.repository.NotificationRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.infrastructure.config.AbstractIntegrationTest
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

/**
 * NotificationController 통합 테스트
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("알림 Controller 통합 테스트")
class NotificationControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    private fun createTestNotification(
        userId: UUID,
        type: NotificationType = NotificationType.LIKE,
        title: String = "새로운 좋아요",
        body: String = "testUser님이 게시물을 좋아합니다.",
        isRead: Boolean = false
    ): Notification {
        return notificationRepository.save(
            Notification(
                userId = userId,
                type = type,
                title = title,
                body = body,
                isRead = isRead,
                actorId = UUID.randomUUID(),
                targetType = NotificationTargetType.CONTENT,
                targetId = UUID.randomUUID(),
                data = """{"type": "$type"}"""
            )
        ).block()!!
    }

    @Nested
    @DisplayName("알림 목록 조회 테스트")
    inner class GetNotificationsTest {

        @Test
        @DisplayName("알림 목록 조회 성공")
        fun getNotifications_Success() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            createTestNotification(user.id!!, NotificationType.LIKE)
            createTestNotification(user.id!!, NotificationType.COMMENT)
            createTestNotification(user.id!!, NotificationType.FOLLOW)

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.notifications").isArray
                .jsonPath("$.notifications.length()").isEqualTo(3)
        }

        @Test
        @DisplayName("알림이 없으면 빈 목록 반환")
        fun getNotifications_Empty() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.notifications").isArray
                .jsonPath("$.notifications.length()").isEqualTo(0)
                .jsonPath("$.hasNext").isEqualTo(false)
        }

        @Test
        @DisplayName("커서 기반 페이징 동작 확인")
        fun getNotifications_WithCursor() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            val notifications = mutableListOf<Notification>()
            repeat(5) {
                notifications.add(createTestNotification(user.id!!))
            }
            val cursor = notifications[2].id!!

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri { builder ->
                    builder.path("${ApiPaths.API_V1_NOTIFICATIONS}")
                        .queryParam("cursor", cursor)
                        .queryParam("limit", 10)
                        .build()
                }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.notifications").isArray
        }
    }

    @Nested
    @DisplayName("읽지 않은 알림 수 조회 테스트")
    inner class GetUnreadCountTest {

        @Test
        @DisplayName("읽지 않은 알림 수 조회 성공")
        fun getUnreadCount_Success() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            createTestNotification(user.id!!, isRead = false)
            createTestNotification(user.id!!, isRead = false)
            createTestNotification(user.id!!, isRead = true)

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}/unread-count")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.unreadCount").isEqualTo(2)
        }

        @Test
        @DisplayName("읽지 않은 알림이 없으면 0 반환")
        fun getUnreadCount_Zero() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            createTestNotification(user.id!!, isRead = true)

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}/unread-count")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.unreadCount").isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("알림 읽음 처리 테스트")
    inner class MarkAsReadTest {

        @Test
        @DisplayName("개별 알림 읽음 처리 성공")
        fun markAsRead_Success() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            val notification = createTestNotification(user.id!!, isRead = false)

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .patch()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}/${notification.id}/read")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.isRead").isEqualTo(true)
        }

        @Test
        @DisplayName("존재하지 않는 알림 읽음 처리 시 404 반환")
        fun markAsRead_NotFound() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .patch()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}/99999/read")
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        @DisplayName("모든 알림 읽음 처리 성공")
        fun markAllAsRead_Success() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            createTestNotification(user.id!!, isRead = false)
            createTestNotification(user.id!!, isRead = false)
            createTestNotification(user.id!!, isRead = false)

            // When
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .patch()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}/read-all")
                .exchange()
                .expectStatus().isNoContent

            // Then: 읽지 않은 알림 수가 0인지 확인
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}/unread-count")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.unreadCount").isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("알림 삭제 테스트")
    inner class DeleteNotificationTest {

        @Test
        @DisplayName("알림 삭제 성공")
        fun deleteNotification_Success() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            val notification = createTestNotification(user.id!!)

            // When
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}/${notification.id}")
                .exchange()
                .expectStatus().isNoContent

            // Then: 알림 목록에서 삭제 확인
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.notifications.length()").isEqualTo(0)
        }

        @Test
        @DisplayName("존재하지 않는 알림 삭제 시 404 반환")
        fun deleteNotification_NotFound() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}/99999")
                .exchange()
                .expectStatus().isNotFound
        }
    }

    @Nested
    @DisplayName("인증 테스트")
    inner class AuthenticationTest {

        @Test
        @DisplayName("인증되지 않은 사용자의 알림 조회 시 401 반환")
        fun getNotifications_Unauthorized() {
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        @DisplayName("인증되지 않은 사용자의 알림 삭제 시 401 반환")
        fun deleteNotification_Unauthorized() {
            webTestClient
                .delete()
                .uri("${ApiPaths.API_V1_NOTIFICATIONS}/1")
                .exchange()
                .expectStatus().isUnauthorized
        }
    }
}
