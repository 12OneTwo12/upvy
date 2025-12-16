package me.onetwo.upvy.domain.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.domain.notification.model.DeliveryStatus
import me.onetwo.upvy.domain.notification.model.DeviceType
import me.onetwo.upvy.domain.notification.model.Notification
import me.onetwo.upvy.domain.notification.model.NotificationSettings
import me.onetwo.upvy.domain.notification.model.NotificationTargetType
import me.onetwo.upvy.domain.notification.model.NotificationType
import me.onetwo.upvy.domain.notification.model.PushProvider
import me.onetwo.upvy.domain.notification.model.PushToken
import me.onetwo.upvy.domain.notification.repository.NotificationRepository
import me.onetwo.upvy.domain.notification.repository.PushNotificationLogRepository
import me.onetwo.upvy.domain.notification.repository.PushTokenRepository
import me.onetwo.upvy.infrastructure.notification.push.PushProviderClient
import me.onetwo.upvy.infrastructure.notification.push.PushResult
import me.onetwo.upvy.infrastructure.notification.push.TokenResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * PushNotificationService 단위 테스트
 */
@DisplayName("푸시 알림 발송 Service 테스트")
class PushNotificationServiceTest : BaseReactiveTest {

    private lateinit var notificationService: NotificationService
    private lateinit var notificationSettingsService: NotificationSettingsService
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var pushTokenRepository: PushTokenRepository
    private lateinit var pushLogRepository: PushNotificationLogRepository
    private lateinit var pushProviderClient: PushProviderClient
    private lateinit var objectMapper: ObjectMapper
    private lateinit var pushNotificationService: PushNotificationService

    private val testUserId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val contentId = UUID.randomUUID()
    private val commentId = UUID.randomUUID()
    private val testToken = "ExponentPushToken[test123]"

    @BeforeEach
    fun setUp() {
        notificationService = mockk()
        notificationSettingsService = mockk()
        notificationRepository = mockk()
        pushTokenRepository = mockk()
        pushLogRepository = mockk()
        pushProviderClient = mockk()
        objectMapper = ObjectMapper()

        every { pushProviderClient.providerType } returns PushProvider.EXPO
        every { notificationRepository.updateDeliveryStatus(any(), any()) } returns Mono.just(true)
        every { pushLogRepository.save(any()) } returns Mono.just(mockk())

        pushNotificationService = PushNotificationServiceImpl(
            notificationService = notificationService,
            notificationSettingsService = notificationSettingsService,
            notificationRepository = notificationRepository,
            pushTokenRepository = pushTokenRepository,
            pushLogRepository = pushLogRepository,
            pushProviders = listOf(pushProviderClient),
            objectMapper = objectMapper
        )
    }

    private fun createTestSettings(
        allNotificationsEnabled: Boolean = true,
        likeNotificationsEnabled: Boolean = true,
        commentNotificationsEnabled: Boolean = true,
        followNotificationsEnabled: Boolean = true
    ): NotificationSettings {
        return NotificationSettings(
            id = 1L,
            userId = testUserId,
            allNotificationsEnabled = allNotificationsEnabled,
            likeNotificationsEnabled = likeNotificationsEnabled,
            commentNotificationsEnabled = commentNotificationsEnabled,
            followNotificationsEnabled = followNotificationsEnabled,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private fun createTestNotification(): Notification {
        return Notification(
            id = 1L,
            userId = testUserId,
            type = NotificationType.LIKE,
            title = "새로운 좋아요",
            body = "testUser님이 게시물을 좋아합니다.",
            data = null,
            isRead = false,
            actorId = actorId,
            targetType = NotificationTargetType.CONTENT,
            targetId = contentId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private fun createTestPushToken(): PushToken {
        return PushToken(
            id = 1L,
            userId = testUserId,
            token = testToken,
            deviceId = "test-device",
            deviceType = DeviceType.IOS,
            provider = PushProvider.EXPO,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    @Nested
    @DisplayName("sendLikeNotification 테스트")
    inner class SendLikeNotificationTest {

        @Test
        @DisplayName("좋아요 알림을 성공적으로 발송한다")
        fun sendsLikeNotification() {
            // Given
            val settings = createTestSettings()
            val notification = createTestNotification()
            val pushToken = createTestPushToken()

            every { notificationSettingsService.getSettings(testUserId) } returns Mono.just(settings)
            every { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mono.just(notification)
            every { pushTokenRepository.findByUserId(testUserId) } returns Flux.just(pushToken)
            every { pushProviderClient.sendPushWithResult(any(), any(), any(), any()) } returns Mono.just(
                PushResult(hasSuccess = true, tokenResults = listOf(TokenResult(success = true, messageId = "test-id")))
            )

            // When
            val result = pushNotificationService.sendLikeNotification(
                contentOwnerId = testUserId,
                actorId = actorId,
                actorNickname = "testUser",
                contentId = contentId
            )

            // Then
            StepVerifier.create(result)
                .expectNext(Unit)
                .verifyComplete()

            verify(exactly = 1) { notificationSettingsService.getSettings(testUserId) }
            verify(exactly = 1) { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { pushProviderClient.sendPushWithResult(any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("자기 자신에게는 알림을 보내지 않는다")
        fun doesNotSendToSelf() {
            // When
            val result = pushNotificationService.sendLikeNotification(
                contentOwnerId = actorId,
                actorId = actorId,
                actorNickname = "testUser",
                contentId = contentId
            )

            // Then
            StepVerifier.create(result)
                .expectNext(Unit)
                .verifyComplete()

            verify(exactly = 0) { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("알림 설정이 비활성화되어 있으면 SKIPPED 상태로 저장하고 푸시는 발송하지 않는다")
        fun savesAsSkippedWhenDisabled() {
            // Given
            val settings = createTestSettings(likeNotificationsEnabled = false)
            val notification = createTestNotification()

            every { notificationSettingsService.getSettings(testUserId) } returns Mono.just(settings)
            every { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mono.just(notification)

            // When
            val result = pushNotificationService.sendLikeNotification(
                contentOwnerId = testUserId,
                actorId = actorId,
                actorNickname = "testUser",
                contentId = contentId
            )

            // Then
            StepVerifier.create(result)
                .expectNext(Unit)
                .verifyComplete()

            verify(exactly = 1) { notificationSettingsService.getSettings(testUserId) }
            verify(exactly = 1) { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { notificationRepository.updateDeliveryStatus(notification.id!!, DeliveryStatus.SKIPPED) }
            verify(exactly = 0) { pushProviderClient.sendPushWithResult(any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("전체 알림이 비활성화되어 있으면 SKIPPED 상태로 저장하고 푸시는 발송하지 않는다")
        fun savesAsSkippedWhenAllDisabled() {
            // Given
            val settings = createTestSettings(allNotificationsEnabled = false)
            val notification = createTestNotification()

            every { notificationSettingsService.getSettings(testUserId) } returns Mono.just(settings)
            every { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mono.just(notification)

            // When
            val result = pushNotificationService.sendLikeNotification(
                contentOwnerId = testUserId,
                actorId = actorId,
                actorNickname = "testUser",
                contentId = contentId
            )

            // Then
            StepVerifier.create(result)
                .expectNext(Unit)
                .verifyComplete()

            verify(exactly = 1) { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { notificationRepository.updateDeliveryStatus(notification.id!!, DeliveryStatus.SKIPPED) }
            verify(exactly = 0) { pushProviderClient.sendPushWithResult(any(), any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("sendCommentNotification 테스트")
    inner class SendCommentNotificationTest {

        @Test
        @DisplayName("댓글 알림을 성공적으로 발송한다")
        fun sendsCommentNotification() {
            // Given
            val settings = createTestSettings()
            val notification = createTestNotification()
            val pushToken = createTestPushToken()

            every { notificationSettingsService.getSettings(testUserId) } returns Mono.just(settings)
            every { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mono.just(notification)
            every { pushTokenRepository.findByUserId(testUserId) } returns Flux.just(pushToken)
            every { pushProviderClient.sendPushWithResult(any(), any(), any(), any()) } returns Mono.just(
                PushResult(hasSuccess = true, tokenResults = listOf(TokenResult(success = true, messageId = "test-id")))
            )

            // When
            val result = pushNotificationService.sendCommentNotification(
                contentOwnerId = testUserId,
                actorId = actorId,
                actorNickname = "testUser",
                contentId = contentId,
                commentId = commentId
            )

            // Then
            StepVerifier.create(result)
                .expectNext(Unit)
                .verifyComplete()

            verify(exactly = 1) { notificationService.createNotification(any(), eq(NotificationType.COMMENT), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("댓글 알림 설정이 비활성화되어 있으면 SKIPPED 상태로 저장하고 푸시는 발송하지 않는다")
        fun savesAsSkippedWhenDisabled() {
            // Given
            val settings = createTestSettings(commentNotificationsEnabled = false)
            val notification = createTestNotification()

            every { notificationSettingsService.getSettings(testUserId) } returns Mono.just(settings)
            every { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mono.just(notification)

            // When
            val result = pushNotificationService.sendCommentNotification(
                contentOwnerId = testUserId,
                actorId = actorId,
                actorNickname = "testUser",
                contentId = contentId,
                commentId = commentId
            )

            // Then
            StepVerifier.create(result)
                .expectNext(Unit)
                .verifyComplete()

            verify(exactly = 1) { notificationService.createNotification(any(), eq(NotificationType.COMMENT), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { notificationRepository.updateDeliveryStatus(notification.id!!, DeliveryStatus.SKIPPED) }
            verify(exactly = 0) { pushProviderClient.sendPushWithResult(any(), any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("sendFollowNotification 테스트")
    inner class SendFollowNotificationTest {

        @Test
        @DisplayName("팔로우 알림을 성공적으로 발송한다")
        fun sendsFollowNotification() {
            // Given
            val settings = createTestSettings()
            val notification = createTestNotification()
            val pushToken = createTestPushToken()

            every { notificationSettingsService.getSettings(testUserId) } returns Mono.just(settings)
            every { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mono.just(notification)
            every { pushTokenRepository.findByUserId(testUserId) } returns Flux.just(pushToken)
            every { pushProviderClient.sendPushWithResult(any(), any(), any(), any()) } returns Mono.just(
                PushResult(hasSuccess = true, tokenResults = listOf(TokenResult(success = true, messageId = "test-id")))
            )

            // When
            val result = pushNotificationService.sendFollowNotification(
                followedUserId = testUserId,
                actorId = actorId,
                actorNickname = "testUser"
            )

            // Then
            StepVerifier.create(result)
                .expectNext(Unit)
                .verifyComplete()

            verify(exactly = 1) { notificationService.createNotification(any(), eq(NotificationType.FOLLOW), any(), any(), any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("팔로우 알림 설정이 비활성화되어 있으면 SKIPPED 상태로 저장하고 푸시는 발송하지 않는다")
        fun savesAsSkippedWhenDisabled() {
            // Given
            val settings = createTestSettings(followNotificationsEnabled = false)
            val notification = createTestNotification()

            every { notificationSettingsService.getSettings(testUserId) } returns Mono.just(settings)
            every { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mono.just(notification)

            // When
            val result = pushNotificationService.sendFollowNotification(
                followedUserId = testUserId,
                actorId = actorId,
                actorNickname = "testUser"
            )

            // Then
            StepVerifier.create(result)
                .expectNext(Unit)
                .verifyComplete()

            verify(exactly = 1) { notificationService.createNotification(any(), eq(NotificationType.FOLLOW), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { notificationRepository.updateDeliveryStatus(notification.id!!, DeliveryStatus.SKIPPED) }
            verify(exactly = 0) { pushProviderClient.sendPushWithResult(any(), any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("푸시 토큰이 없는 경우 테스트")
    inner class NoPushTokenTest {

        @Test
        @DisplayName("푸시 토큰이 없으면 알림은 저장하고 SKIPPED 상태로 표시하며 푸시는 발송하지 않는다")
        fun savesNotificationAsSkippedAndDoesNotSendPush() {
            // Given
            val settings = createTestSettings()
            val notification = createTestNotification()

            every { notificationSettingsService.getSettings(testUserId) } returns Mono.just(settings)
            every { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) } returns Mono.just(notification)
            every { pushTokenRepository.findByUserId(testUserId) } returns Flux.empty()

            // When
            val result = pushNotificationService.sendLikeNotification(
                contentOwnerId = testUserId,
                actorId = actorId,
                actorNickname = "testUser",
                contentId = contentId
            )

            // Then
            StepVerifier.create(result)
                .expectNext(Unit)
                .verifyComplete()

            verify(exactly = 1) { notificationService.createNotification(any(), any(), any(), any(), any(), any(), any(), any()) }
            verify(exactly = 1) { notificationRepository.updateDeliveryStatus(notification.id!!, DeliveryStatus.SKIPPED) }
            verify(exactly = 0) { pushProviderClient.sendPushWithResult(any(), any(), any(), any()) }
        }
    }
}
