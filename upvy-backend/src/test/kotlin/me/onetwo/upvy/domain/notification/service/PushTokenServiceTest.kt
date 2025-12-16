package me.onetwo.upvy.domain.notification.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.domain.notification.dto.RegisterPushTokenRequest
import me.onetwo.upvy.domain.notification.model.DeviceType
import me.onetwo.upvy.domain.notification.model.PushProvider
import me.onetwo.upvy.domain.notification.model.PushToken
import me.onetwo.upvy.domain.notification.repository.PushTokenRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * PushTokenService 단위 테스트
 */
@DisplayName("푸시 토큰 Service 테스트")
class PushTokenServiceTest : BaseReactiveTest {

    private lateinit var pushTokenRepository: PushTokenRepository
    private lateinit var pushTokenService: PushTokenService

    private val testUserId = UUID.randomUUID()
    private val testDeviceId = "test-device-id"
    private val testToken = "ExponentPushToken[test123]"

    @BeforeEach
    fun setUp() {
        pushTokenRepository = mockk()
        pushTokenService = PushTokenServiceImpl(pushTokenRepository)
    }

    private fun createTestPushToken(
        id: Long = 1L,
        userId: UUID = testUserId,
        token: String = testToken,
        deviceId: String = testDeviceId,
        deviceType: DeviceType = DeviceType.IOS,
        provider: PushProvider = PushProvider.EXPO
    ): PushToken {
        return PushToken(
            id = id,
            userId = userId,
            token = token,
            deviceId = deviceId,
            deviceType = deviceType,
            provider = provider,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    @Nested
    @DisplayName("registerToken 테스트")
    inner class RegisterTokenTest {

        @Test
        @DisplayName("새 토큰을 등록하면 저장 후 응답을 반환한다")
        fun registersNewToken() {
            // Given
            val request = RegisterPushTokenRequest(
                token = testToken,
                deviceId = testDeviceId,
                deviceType = DeviceType.IOS,
                provider = PushProvider.EXPO
            )
            val savedToken = createTestPushToken()
            every { pushTokenRepository.saveOrUpdate(any()) } returns Mono.just(savedToken)

            // When
            val result = pushTokenService.registerToken(testUserId, request)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { response ->
                    response.token == testToken &&
                        response.deviceId == testDeviceId &&
                        response.deviceType == DeviceType.IOS &&
                        response.provider == PushProvider.EXPO
                }
                .verifyComplete()

            verify(exactly = 1) { pushTokenRepository.saveOrUpdate(any()) }
        }

        @Test
        @DisplayName("기존 토큰이 있으면 업데이트 후 응답을 반환한다")
        fun updatesExistingToken() {
            // Given
            val newToken = "ExponentPushToken[newToken123]"
            val request = RegisterPushTokenRequest(
                token = newToken,
                deviceId = testDeviceId,
                deviceType = DeviceType.IOS,
                provider = PushProvider.EXPO
            )
            val updatedToken = createTestPushToken(token = newToken)

            every { pushTokenRepository.saveOrUpdate(any()) } returns Mono.just(updatedToken)

            // When
            val result = pushTokenService.registerToken(testUserId, request)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { response ->
                    response.token == newToken &&
                        response.deviceId == testDeviceId
                }
                .verifyComplete()

            verify(exactly = 1) { pushTokenRepository.saveOrUpdate(any()) }
        }

        @Test
        @DisplayName("FCM 토큰을 등록할 수 있다")
        fun registersFcmToken() {
            // Given
            val fcmToken = "fcm_token_123"
            val request = RegisterPushTokenRequest(
                token = fcmToken,
                deviceId = testDeviceId,
                deviceType = DeviceType.ANDROID,
                provider = PushProvider.FCM
            )
            val savedToken = createTestPushToken(
                token = fcmToken,
                deviceType = DeviceType.ANDROID,
                provider = PushProvider.FCM
            )
            every { pushTokenRepository.saveOrUpdate(any()) } returns Mono.just(savedToken)

            // When
            val result = pushTokenService.registerToken(testUserId, request)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { response ->
                    response.token == fcmToken &&
                        response.provider == PushProvider.FCM &&
                        response.deviceType == DeviceType.ANDROID
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("deleteToken 테스트")
    inner class DeleteTokenTest {

        @Test
        @DisplayName("특정 디바이스 토큰을 삭제한다")
        fun deletesTokenForDevice() {
            // Given
            every { pushTokenRepository.deleteByUserIdAndDeviceId(testUserId, testDeviceId) } returns Mono.empty()

            // When
            val result = pushTokenService.deleteToken(testUserId, testDeviceId)

            // Then
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { pushTokenRepository.deleteByUserIdAndDeviceId(testUserId, testDeviceId) }
        }
    }

    @Nested
    @DisplayName("deleteAllTokens 테스트")
    inner class DeleteAllTokensTest {

        @Test
        @DisplayName("사용자의 모든 토큰을 삭제한다")
        fun deletesAllTokensForUser() {
            // Given
            every { pushTokenRepository.deleteAllByUserId(testUserId) } returns Mono.empty()

            // When
            val result = pushTokenService.deleteAllTokens(testUserId)

            // Then
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { pushTokenRepository.deleteAllByUserId(testUserId) }
        }
    }
}
