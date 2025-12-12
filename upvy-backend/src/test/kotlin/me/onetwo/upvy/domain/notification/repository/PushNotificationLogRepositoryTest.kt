package me.onetwo.upvy.domain.notification.repository

import me.onetwo.upvy.domain.notification.model.DeviceType
import me.onetwo.upvy.domain.notification.model.Notification
import me.onetwo.upvy.domain.notification.model.NotificationTargetType
import me.onetwo.upvy.domain.notification.model.NotificationType
import me.onetwo.upvy.domain.notification.model.PushLogStatus
import me.onetwo.upvy.domain.notification.model.PushNotificationLog
import me.onetwo.upvy.domain.notification.model.PushProvider
import me.onetwo.upvy.domain.notification.model.PushToken
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * PushNotificationLogRepository 통합 테스트
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@DisplayName("푸시 알림 로그 Repository 통합 테스트")
class PushNotificationLogRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var pushNotificationLogRepository: PushNotificationLogRepository

    @Autowired
    private lateinit var notificationRepository: NotificationRepository

    @Autowired
    private lateinit var pushTokenRepository: PushTokenRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var testUser: User
    private lateinit var testNotification: Notification
    private lateinit var testPushToken: PushToken

    @BeforeEach
    fun setUp() {
        testUser = userRepository.save(
            User(
                email = "test-${UUID.randomUUID()}@example.com"
            )
        ).block()!!

        testNotification = notificationRepository.save(
            Notification(
                userId = testUser.id!!,
                type = NotificationType.LIKE,
                title = "새로운 좋아요",
                body = "testUser님이 게시물을 좋아합니다.",
                isRead = false,
                actorId = UUID.randomUUID(),
                targetType = NotificationTargetType.CONTENT,
                targetId = UUID.randomUUID(),
                data = """{"type": "LIKE"}"""
            )
        ).block()!!

        testPushToken = pushTokenRepository.save(
            PushToken(
                userId = testUser.id!!,
                token = "ExponentPushToken[test123]",
                deviceId = "test-device-id",
                deviceType = DeviceType.IOS,
                provider = PushProvider.EXPO
            )
        ).block()!!
    }

    private fun createTestLog(
        notificationId: Long = testNotification.id!!,
        pushTokenId: Long? = testPushToken.id,
        provider: PushProvider = PushProvider.EXPO,
        status: PushLogStatus = PushLogStatus.SENT,
        providerMessageId: String? = "test-message-id",
        errorCode: String? = null,
        errorMessage: String? = null,
        sentAt: Instant = Instant.now()
    ): PushNotificationLog {
        return PushNotificationLog(
            notificationId = notificationId,
            pushTokenId = pushTokenId,
            provider = provider,
            status = status,
            providerMessageId = providerMessageId,
            errorCode = errorCode,
            errorMessage = errorMessage,
            attemptCount = 1,
            sentAt = sentAt
        )
    }

    @Nested
    @DisplayName("save 테스트")
    inner class SaveTest {

        @Test
        @DisplayName("발송 로그를 저장하고 ID를 반환한다")
        fun savesLogAndReturnsWithId() {
            // Given
            val log = createTestLog()

            // When
            val result = pushNotificationLogRepository.save(log)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.id != null &&
                        saved.notificationId == testNotification.id &&
                        saved.pushTokenId == testPushToken.id &&
                        saved.provider == PushProvider.EXPO &&
                        saved.status == PushLogStatus.SENT &&
                        saved.providerMessageId == "test-message-id"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("실패 상태의 로그를 저장할 수 있다")
        fun savesFailedLog() {
            // Given
            val log = createTestLog(
                status = PushLogStatus.FAILED,
                errorCode = "DeviceNotRegistered",
                errorMessage = "The device is no longer registered."
            )

            // When
            val result = pushNotificationLogRepository.save(log)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.id != null &&
                        saved.status == PushLogStatus.FAILED &&
                        saved.errorCode == "DeviceNotRegistered" &&
                        saved.errorMessage == "The device is no longer registered."
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("FCM 프로바이더 로그를 저장할 수 있다")
        fun savesFcmProviderLog() {
            // Given
            val log = createTestLog(
                provider = PushProvider.FCM,
                providerMessageId = "projects/xxx/messages/123"
            )

            // When
            val result = pushNotificationLogRepository.save(log)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.provider == PushProvider.FCM &&
                        saved.providerMessageId == "projects/xxx/messages/123"
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findByNotificationId 테스트")
    inner class FindByNotificationIdTest {

        @Test
        @DisplayName("알림 ID로 발송 로그 목록을 조회한다")
        fun findsLogsByNotificationId() {
            // Given
            pushNotificationLogRepository.save(createTestLog()).block()
            pushNotificationLogRepository.save(createTestLog(providerMessageId = "msg-2")).block()

            // When
            val result = pushNotificationLogRepository.findByNotificationId(testNotification.id!!)

            // Then
            StepVerifier.create(result.collectList())
                .expectNextMatches { logs ->
                    logs.size == 2 &&
                        logs.all { it.notificationId == testNotification.id }
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("발송 로그가 없으면 빈 결과를 반환한다")
        fun returnsEmptyIfNoLogs() {
            // Given
            val nonExistingNotificationId = 99999L

            // When
            val result = pushNotificationLogRepository.findByNotificationId(nonExistingNotificationId)

            // Then
            StepVerifier.create(result.collectList())
                .expectNextMatches { logs -> logs.isEmpty() }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findByPushTokenId 테스트")
    inner class FindByPushTokenIdTest {

        @Test
        @DisplayName("푸시 토큰 ID로 발송 로그 목록을 조회한다")
        fun findsLogsByPushTokenId() {
            // Given
            pushNotificationLogRepository.save(createTestLog()).block()

            // 다른 알림에 대한 로그 추가
            val anotherNotification = notificationRepository.save(
                Notification(
                    userId = testUser.id!!,
                    type = NotificationType.COMMENT,
                    title = "새로운 댓글",
                    body = "댓글이 달렸습니다.",
                    isRead = false,
                    actorId = UUID.randomUUID(),
                    targetType = NotificationTargetType.CONTENT,
                    targetId = UUID.randomUUID()
                )
            ).block()!!
            pushNotificationLogRepository.save(createTestLog(notificationId = anotherNotification.id!!)).block()

            // When
            val result = pushNotificationLogRepository.findByPushTokenId(testPushToken.id!!)

            // Then
            StepVerifier.create(result.collectList())
                .expectNextMatches { logs ->
                    logs.size == 2 &&
                        logs.all { it.pushTokenId == testPushToken.id }
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("limit 파라미터가 적용된다")
        fun appliesLimitParameter() {
            // Given
            repeat(5) {
                val notification = notificationRepository.save(
                    Notification(
                        userId = testUser.id!!,
                        type = NotificationType.LIKE,
                        title = "알림 $it",
                        body = "본문",
                        isRead = false
                    )
                ).block()!!
                pushNotificationLogRepository.save(createTestLog(notificationId = notification.id!!)).block()
            }

            // When
            val result = pushNotificationLogRepository.findByPushTokenId(testPushToken.id!!, limit = 3)

            // Then
            StepVerifier.create(result.collectList())
                .expectNextMatches { logs -> logs.size == 3 }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("countByStatusAndPeriod 테스트")
    inner class CountByStatusAndPeriodTest {

        @Test
        @DisplayName("기간별 상태별 발송 로그 개수를 조회한다")
        fun countsLogsByStatusAndPeriod() {
            // Given
            val now = Instant.now()
            val sentAt = now.minus(1, ChronoUnit.HOURS)

            pushNotificationLogRepository.save(createTestLog(status = PushLogStatus.SENT, sentAt = sentAt)).block()
            pushNotificationLogRepository.save(
                createTestLog(
                    status = PushLogStatus.SENT,
                    sentAt = sentAt,
                    providerMessageId = "msg-2"
                )
            ).block()
            pushNotificationLogRepository.save(
                createTestLog(
                    status = PushLogStatus.FAILED,
                    sentAt = sentAt,
                    providerMessageId = "msg-3"
                )
            ).block()

            // When
            val result = pushNotificationLogRepository.countByStatusAndPeriod(
                status = PushLogStatus.SENT,
                from = now.minus(2, ChronoUnit.HOURS),
                to = now
            )

            // Then
            StepVerifier.create(result)
                .expectNext(2L)
                .verifyComplete()
        }

        @Test
        @DisplayName("해당 기간에 로그가 없으면 0을 반환한다")
        fun returnsZeroIfNoLogsInPeriod() {
            // Given
            val now = Instant.now()

            // When
            val result = pushNotificationLogRepository.countByStatusAndPeriod(
                status = PushLogStatus.SENT,
                from = now.minus(2, ChronoUnit.HOURS),
                to = now.minus(1, ChronoUnit.HOURS)
            )

            // Then
            StepVerifier.create(result)
                .expectNext(0L)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findLatestByNotificationId 테스트")
    inner class FindLatestByNotificationIdTest {

        @Test
        @DisplayName("알림 ID로 최신 발송 로그를 조회한다")
        fun findsLatestLogByNotificationId() {
            // Given
            pushNotificationLogRepository.save(createTestLog(providerMessageId = "msg-1")).block()
            Thread.sleep(10) // 시간 차이를 두기 위해
            pushNotificationLogRepository.save(createTestLog(providerMessageId = "msg-2")).block()

            // When
            val result = pushNotificationLogRepository.findLatestByNotificationId(testNotification.id!!)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { log ->
                    log.notificationId == testNotification.id &&
                        log.providerMessageId == "msg-2"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("발송 로그가 없으면 empty를 반환한다")
        fun returnsEmptyIfNoLogs() {
            // Given
            val nonExistingNotificationId = 99999L

            // When
            val result = pushNotificationLogRepository.findLatestByNotificationId(nonExistingNotificationId)

            // Then
            StepVerifier.create(result)
                .verifyComplete()
        }
    }
}
