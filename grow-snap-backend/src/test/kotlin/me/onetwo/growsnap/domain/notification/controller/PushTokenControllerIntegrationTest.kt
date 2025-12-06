package me.onetwo.growsnap.domain.notification.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.notification.dto.RegisterPushTokenRequest
import me.onetwo.growsnap.domain.notification.model.DeviceType
import me.onetwo.growsnap.domain.notification.model.PushProvider
import me.onetwo.growsnap.domain.notification.model.PushToken
import me.onetwo.growsnap.domain.notification.repository.PushTokenRepository
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
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

/**
 * PushTokenController 통합 테스트
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("푸시 토큰 Controller 통합 테스트")
class PushTokenControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var pushTokenRepository: PushTokenRepository

    private fun createTestPushToken(
        userId: UUID,
        token: String = "ExponentPushToken[test123]",
        deviceId: String = "test-device-id",
        deviceType: DeviceType = DeviceType.IOS,
        provider: PushProvider = PushProvider.EXPO
    ): PushToken {
        return pushTokenRepository.save(
            PushToken(
                userId = userId,
                token = token,
                deviceId = deviceId,
                deviceType = deviceType,
                provider = provider
            )
        ).block()!!
    }

    @Nested
    @DisplayName("푸시 토큰 등록 테스트")
    inner class RegisterPushTokenTest {

        @Test
        @DisplayName("새 푸시 토큰 등록 성공")
        fun registerPushToken_Success() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            val request = RegisterPushTokenRequest(
                token = "ExponentPushToken[newToken]",
                deviceId = "new-device-id",
                deviceType = DeviceType.IOS,
                provider = PushProvider.EXPO
            )

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri(ApiPaths.API_V1_PUSH_TOKENS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.token").isEqualTo("ExponentPushToken[newToken]")
                .jsonPath("$.deviceId").isEqualTo("new-device-id")
                .jsonPath("$.deviceType").isEqualTo("IOS")
                .jsonPath("$.provider").isEqualTo("EXPO")
        }

        @Test
        @DisplayName("FCM 토큰 등록 성공")
        fun registerFcmToken_Success() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            val request = RegisterPushTokenRequest(
                token = "fcm_token_123",
                deviceId = "android-device-id",
                deviceType = DeviceType.ANDROID,
                provider = PushProvider.FCM
            )

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri(ApiPaths.API_V1_PUSH_TOKENS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.token").isEqualTo("fcm_token_123")
                .jsonPath("$.deviceType").isEqualTo("ANDROID")
                .jsonPath("$.provider").isEqualTo("FCM")
        }

        @Test
        @DisplayName("기존 토큰 업데이트 성공")
        fun updateExistingToken_Success() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            createTestPushToken(user.id!!, deviceId = "existing-device")

            val request = RegisterPushTokenRequest(
                token = "ExponentPushToken[updatedToken]",
                deviceId = "existing-device",
                deviceType = DeviceType.IOS,
                provider = PushProvider.EXPO
            )

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri(ApiPaths.API_V1_PUSH_TOKENS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.token").isEqualTo("ExponentPushToken[updatedToken]")
                .jsonPath("$.deviceId").isEqualTo("existing-device")
        }

        @Test
        @DisplayName("잘못된 요청 시 400 반환")
        fun registerPushToken_InvalidRequest() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri(ApiPaths.API_V1_PUSH_TOKENS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"token": "", "deviceId": "", "deviceType": "IOS", "provider": "EXPO"}""")
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    @DisplayName("푸시 토큰 삭제 테스트")
    inner class DeletePushTokenTest {

        @Test
        @DisplayName("특정 디바이스 토큰 삭제 성공")
        fun deleteToken_Success() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            createTestPushToken(user.id!!, deviceId = "to-delete-device")

            // When
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_PUSH_TOKENS}/to-delete-device")
                .exchange()
                .expectStatus().isNoContent

            // Then: 토큰이 삭제되었는지 확인
            val remainingTokens = pushTokenRepository.findByUserId(user.id!!).collectList().block()
            assert(remainingTokens!!.isEmpty())
        }

        @Test
        @DisplayName("존재하지 않는 토큰 삭제 시에도 204 반환")
        fun deleteNonExistingToken_Success() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_PUSH_TOKENS}/non-existing-device")
                .exchange()
                .expectStatus().isNoContent
        }
    }

    @Nested
    @DisplayName("모든 푸시 토큰 삭제 테스트")
    inner class DeleteAllPushTokensTest {

        @Test
        @DisplayName("사용자의 모든 토큰 삭제 성공")
        fun deleteAllTokens_Success() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")
            createTestPushToken(user.id!!, deviceId = "device-1")
            createTestPushToken(user.id!!, deviceId = "device-2")
            createTestPushToken(user.id!!, deviceId = "device-3")

            // When
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri(ApiPaths.API_V1_PUSH_TOKENS)
                .exchange()
                .expectStatus().isNoContent

            // Then: 모든 토큰이 삭제되었는지 확인
            val remainingTokens = pushTokenRepository.findByUserId(user.id!!).collectList().block()
            assert(remainingTokens!!.isEmpty())
        }

        @Test
        @DisplayName("토큰이 없어도 204 반환")
        fun deleteAllTokens_NoTokens() {
            // Given
            val (user, _) = createUserWithProfile(userRepository, userProfileRepository, email = "test@example.com")

            // When & Then
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri(ApiPaths.API_V1_PUSH_TOKENS)
                .exchange()
                .expectStatus().isNoContent
        }
    }

    @Nested
    @DisplayName("인증 테스트")
    inner class AuthenticationTest {

        @Test
        @DisplayName("인증되지 않은 사용자의 토큰 등록 시 401 반환")
        fun registerToken_Unauthorized() {
            val request = RegisterPushTokenRequest(
                token = "ExponentPushToken[test]",
                deviceId = "device-id",
                deviceType = DeviceType.IOS,
                provider = PushProvider.EXPO
            )

            webTestClient
                .post()
                .uri(ApiPaths.API_V1_PUSH_TOKENS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        @DisplayName("인증되지 않은 사용자의 토큰 삭제 시 401 반환")
        fun deleteToken_Unauthorized() {
            webTestClient
                .delete()
                .uri("${ApiPaths.API_V1_PUSH_TOKENS}/device-id")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        @DisplayName("인증되지 않은 사용자의 모든 토큰 삭제 시 401 반환")
        fun deleteAllTokens_Unauthorized() {
            webTestClient
                .delete()
                .uri(ApiPaths.API_V1_PUSH_TOKENS)
                .exchange()
                .expectStatus().isUnauthorized
        }
    }
}
