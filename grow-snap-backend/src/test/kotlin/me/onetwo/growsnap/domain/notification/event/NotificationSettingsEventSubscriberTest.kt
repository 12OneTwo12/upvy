package me.onetwo.growsnap.domain.notification.event

import me.onetwo.growsnap.domain.user.event.UserCreatedEvent
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.infrastructure.config.AbstractIntegrationTest
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
import me.onetwo.growsnap.jooq.generated.tables.references.NOTIFICATION_SETTINGS
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * NotificationSettingsEventSubscriber 통합 테스트
 *
 * Reactor Sinks API를 사용하여 NotificationSettingsEventSubscriber가
 * UserCreatedEvent를 수신하고 기본 알림 설정을 생성하는지 검증합니다.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@DisplayName("알림 설정 이벤트 Subscriber 통합 테스트")
class NotificationSettingsEventSubscriberTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var eventPublisher: ReactiveEventPublisher

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        // Given: 테스트 사용자 생성
        testUser = User(
            id = UUID.randomUUID(),
            email = "notification-event-test@example.com",
            provider = OAuthProvider.GOOGLE,
            providerId = "google-notification-event-12345",
            role = UserRole.USER
        )
        userRepository.save(testUser).block()

        val testProfile = UserProfile(
            userId = testUser.id!!,
            nickname = "알림테스트유저",
            profileImageUrl = null,
            bio = null
        )
        userProfileRepository.save(testProfile).block()
    }

    @Test
    @DisplayName("UserCreatedEvent 발행 시, 기본 알림 설정이 비동기로 생성된다")
    fun handleUserCreatedEvent_WithEvent_CreatesDefaultSettings() {
        // Given: 사용자 생성 이벤트
        val event = UserCreatedEvent(userId = testUser.id!!)

        // When: 이벤트 발행 (Reactor Sinks API)
        eventPublisher.publish(event)

        // Then: Awaitility로 비동기 처리 대기
        await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            val count = Mono.from(
                dslContext.selectCount()
                    .from(NOTIFICATION_SETTINGS)
                    .where(NOTIFICATION_SETTINGS.USER_ID.eq(testUser.id.toString()))
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            assertThat(count).isEqualTo(1)
        }
    }

    @Test
    @DisplayName("UserCreatedEvent 발행 시, 모든 알림이 기본적으로 활성화된다")
    fun handleUserCreatedEvent_WithEvent_AllNotificationsEnabled() {
        // Given: 사용자 생성 이벤트
        val event = UserCreatedEvent(userId = testUser.id!!)

        // When: 이벤트 발행 (Reactor Sinks API)
        eventPublisher.publish(event)

        // Then: Awaitility로 비동기 처리 대기
        await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            val settings = Mono.from(
                dslContext.select(
                    NOTIFICATION_SETTINGS.ALL_NOTIFICATIONS_ENABLED,
                    NOTIFICATION_SETTINGS.LIKE_NOTIFICATIONS_ENABLED,
                    NOTIFICATION_SETTINGS.COMMENT_NOTIFICATIONS_ENABLED,
                    NOTIFICATION_SETTINGS.FOLLOW_NOTIFICATIONS_ENABLED
                )
                    .from(NOTIFICATION_SETTINGS)
                    .where(NOTIFICATION_SETTINGS.USER_ID.eq(testUser.id.toString()))
            ).block()

            assertThat(settings).isNotNull
            assertThat(settings!!.value1()).isTrue()  // allNotificationsEnabled
            assertThat(settings.value2()).isTrue()     // likeNotificationsEnabled
            assertThat(settings.value3()).isTrue()     // commentNotificationsEnabled
            assertThat(settings.value4()).isTrue()     // followNotificationsEnabled
        }
    }

    @Test
    @DisplayName("동일한 사용자에 대해 여러 번 이벤트가 발행되어도 중복 설정이 생성되지 않는다")
    fun handleUserCreatedEvent_DuplicateEvents_NoMultipleSettings() {
        // Given: 동일한 사용자에 대해 여러 번 이벤트 발행
        val event = UserCreatedEvent(userId = testUser.id!!)

        // When: 3번 이벤트 발행 (Reactor Sinks API)
        eventPublisher.publish(event)
        eventPublisher.publish(event)
        eventPublisher.publish(event)

        // Then: Awaitility로 비동기 처리 대기 - 설정은 1개만 생성됨 (또는 에러 발생으로 중복 방지)
        await.pollDelay(500, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted {
            val count = Mono.from(
                dslContext.selectCount()
                    .from(NOTIFICATION_SETTINGS)
                    .where(NOTIFICATION_SETTINGS.USER_ID.eq(testUser.id.toString()))
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .block()
            // user_id UNIQUE 제약조건으로 인해 첫 번째 이벤트만 성공하고 나머지는 무시됨
            assertThat(count).isEqualTo(1)
        }
    }
}
