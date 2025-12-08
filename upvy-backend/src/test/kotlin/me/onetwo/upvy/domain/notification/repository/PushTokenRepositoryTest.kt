package me.onetwo.upvy.domain.notification.repository

import me.onetwo.upvy.domain.notification.model.DeviceType
import me.onetwo.upvy.domain.notification.model.PushProvider
import me.onetwo.upvy.domain.notification.model.PushToken
import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
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
 * PushTokenRepository 통합 테스트
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@DisplayName("푸시 토큰 Repository 통합 테스트")
class PushTokenRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var pushTokenRepository: PushTokenRepository

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

    private fun createTestPushToken(
        userId: UUID,
        token: String = "ExponentPushToken[test123]",
        deviceId: String = "test-device-id",
        deviceType: DeviceType = DeviceType.IOS,
        provider: PushProvider = PushProvider.EXPO
    ): PushToken {
        return PushToken(
            userId = userId,
            token = token,
            deviceId = deviceId,
            deviceType = deviceType,
            provider = provider
        )
    }

    @Nested
    @DisplayName("save 테스트")
    inner class SaveTest {

        @Test
        @DisplayName("푸시 토큰을 저장하고 ID를 반환한다")
        fun savesTokenAndReturnsWithId() {
            // Given
            val user = createTestUser()
            val pushToken = createTestPushToken(user.id!!)

            // When
            val result = pushTokenRepository.save(pushToken)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.id != null &&
                        saved.userId == user.id &&
                        saved.token == "ExponentPushToken[test123]" &&
                        saved.deviceId == "test-device-id" &&
                        saved.deviceType == DeviceType.IOS &&
                        saved.provider == PushProvider.EXPO
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("FCM 토큰을 저장할 수 있다")
        fun savesFcmToken() {
            // Given
            val user = createTestUser()
            val pushToken = createTestPushToken(
                userId = user.id!!,
                token = "fcm_token_123",
                deviceType = DeviceType.ANDROID,
                provider = PushProvider.FCM
            )

            // When
            val result = pushTokenRepository.save(pushToken)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { saved ->
                    saved.id != null &&
                        saved.provider == PushProvider.FCM &&
                        saved.deviceType == DeviceType.ANDROID
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findByUserId 테스트")
    inner class FindByUserIdTest {

        @Test
        @DisplayName("사용자의 모든 푸시 토큰을 반환한다")
        fun returnsAllTokensForUser() {
            // Given
            val user = createTestUser()
            val token1 = createTestPushToken(user.id!!, deviceId = "device-1")
            val token2 = createTestPushToken(user.id!!, deviceId = "device-2")
            pushTokenRepository.save(token1).block()
            pushTokenRepository.save(token2).block()

            // When
            val result = pushTokenRepository.findByUserId(user.id!!)

            // Then
            StepVerifier.create(result.collectList())
                .expectNextMatches { tokens ->
                    tokens.size == 2 &&
                        tokens.all { it.userId == user.id }
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("토큰이 없으면 빈 결과를 반환한다")
        fun returnsEmptyIfNoTokens() {
            // Given
            val nonExistingUserId = UUID.randomUUID()

            // When
            val result = pushTokenRepository.findByUserId(nonExistingUserId)

            // Then
            StepVerifier.create(result.collectList())
                .expectNextMatches { tokens -> tokens.isEmpty() }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findByUserIdAndDeviceId 테스트")
    inner class FindByUserIdAndDeviceIdTest {

        @Test
        @DisplayName("특정 디바이스의 토큰을 반환한다")
        fun returnsTokenForDevice() {
            // Given
            val user = createTestUser()
            val pushToken = createTestPushToken(user.id!!, deviceId = "specific-device")
            pushTokenRepository.save(pushToken).block()

            // When
            val result = pushTokenRepository.findByUserIdAndDeviceId(user.id!!, "specific-device")

            // Then
            StepVerifier.create(result)
                .expectNextMatches { found ->
                    found.userId == user.id &&
                        found.deviceId == "specific-device"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("해당 디바이스가 없으면 empty를 반환한다")
        fun returnsEmptyIfDeviceNotFound() {
            // Given
            val user = createTestUser()

            // When
            val result = pushTokenRepository.findByUserIdAndDeviceId(user.id!!, "non-existing-device")

            // Then
            StepVerifier.create(result)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("update 테스트")
    inner class UpdateTest {

        @Test
        @DisplayName("토큰을 업데이트한다")
        fun updatesToken() {
            // Given
            val user = createTestUser()
            val pushToken = createTestPushToken(user.id!!)
            val saved = pushTokenRepository.save(pushToken).block()!!

            val updatedToken = saved.copy(token = "ExponentPushToken[newToken]")

            // When
            val result = pushTokenRepository.update(updatedToken)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { updated ->
                    updated.token == "ExponentPushToken[newToken]"
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("deleteByUserIdAndDeviceId 테스트")
    inner class DeleteByUserIdAndDeviceIdTest {

        @Test
        @DisplayName("특정 디바이스의 토큰을 삭제한다")
        fun deletesTokenForDevice() {
            // Given
            val user = createTestUser()
            val pushToken = createTestPushToken(user.id!!, deviceId = "to-delete")
            pushTokenRepository.save(pushToken).block()

            // When
            val result = pushTokenRepository.deleteByUserIdAndDeviceId(user.id!!, "to-delete")

            // Then
            StepVerifier.create(result)
                .verifyComplete()

            // Verify deletion
            StepVerifier.create(pushTokenRepository.findByUserIdAndDeviceId(user.id!!, "to-delete"))
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("deleteAllByUserId 테스트")
    inner class DeleteAllByUserIdTest {

        @Test
        @DisplayName("사용자의 모든 토큰을 삭제한다")
        fun deletesAllTokensForUser() {
            // Given
            val user = createTestUser()
            pushTokenRepository.save(createTestPushToken(user.id!!, deviceId = "device-1")).block()
            pushTokenRepository.save(createTestPushToken(user.id!!, deviceId = "device-2")).block()

            // When
            val result = pushTokenRepository.deleteAllByUserId(user.id!!)

            // Then
            StepVerifier.create(result)
                .verifyComplete()

            // Verify all deleted
            StepVerifier.create(pushTokenRepository.findByUserId(user.id!!).collectList())
                .expectNextMatches { tokens -> tokens.isEmpty() }
                .verifyComplete()
        }
    }
}
