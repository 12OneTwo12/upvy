package me.onetwo.upvy.domain.notification.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.notification.dto.PushTokenResponse
import me.onetwo.upvy.domain.notification.dto.RegisterPushTokenRequest
import me.onetwo.upvy.domain.notification.model.DeviceType
import me.onetwo.upvy.domain.notification.model.PushProvider
import me.onetwo.upvy.domain.notification.service.PushTokenService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * PushTokenController 단위 테스트 + Spring REST Docs
 */
@WebFluxTest(controllers = [PushTokenController::class])
@Import(TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("푸시 토큰 Controller 테스트")
class PushTokenControllerTest : BaseReactiveTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var pushTokenService: PushTokenService

    private val testUserId = UUID.randomUUID()
    private val testDeviceId = "test-device-id"
    private val testToken = "ExponentPushToken[test123]"

    @Test
    @DisplayName("푸시 토큰 등록 성공")
    fun registerToken_Success() {
        // Given
        val request = RegisterPushTokenRequest(
            token = testToken,
            deviceId = testDeviceId,
            deviceType = DeviceType.IOS,
            provider = PushProvider.EXPO
        )
        val response = PushTokenResponse(
            token = testToken,
            deviceId = testDeviceId,
            deviceType = DeviceType.IOS,
            provider = PushProvider.EXPO
        )
        every { pushTokenService.registerToken(testUserId, request) } returns Mono.just(response)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .post()
            .uri(ApiPaths.API_V1_PUSH_TOKENS)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.token").isEqualTo(testToken)
            .jsonPath("$.deviceId").isEqualTo(testDeviceId)
            .jsonPath("$.deviceType").isEqualTo("IOS")
            .jsonPath("$.provider").isEqualTo("EXPO")
            .consumeWith(
                document(
                    "push-token-register",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("token").description("푸시 토큰 (Expo: ExponentPushToken[xxx], FCM: fcm_token)"),
                        fieldWithPath("deviceId").description("디바이스 고유 ID"),
                        fieldWithPath("deviceType").description("디바이스 타입 (IOS, ANDROID, WEB, UNKNOWN)"),
                        fieldWithPath("provider").description("푸시 제공자 (EXPO, FCM, APNS)")
                    ),
                    responseFields(
                        fieldWithPath("token").description("등록된 푸시 토큰"),
                        fieldWithPath("deviceId").description("디바이스 ID"),
                        fieldWithPath("deviceType").description("디바이스 타입"),
                        fieldWithPath("provider").description("푸시 제공자")
                    )
                )
            )

        verify(exactly = 1) { pushTokenService.registerToken(testUserId, request) }
    }

    @Test
    @DisplayName("FCM 푸시 토큰 등록 성공")
    fun registerToken_FCM_Success() {
        // Given
        val fcmToken = "fcm_test_token_123"
        val request = RegisterPushTokenRequest(
            token = fcmToken,
            deviceId = testDeviceId,
            deviceType = DeviceType.ANDROID,
            provider = PushProvider.FCM
        )
        val response = PushTokenResponse(
            token = fcmToken,
            deviceId = testDeviceId,
            deviceType = DeviceType.ANDROID,
            provider = PushProvider.FCM
        )
        every { pushTokenService.registerToken(testUserId, request) } returns Mono.just(response)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .post()
            .uri(ApiPaths.API_V1_PUSH_TOKENS)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.provider").isEqualTo("FCM")

        verify(exactly = 1) { pushTokenService.registerToken(testUserId, request) }
    }

    @Test
    @DisplayName("특정 디바이스 푸시 토큰 삭제 성공")
    fun deleteToken_Success() {
        // Given
        every { pushTokenService.deleteToken(testUserId, testDeviceId) } returns Mono.empty()

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .delete()
            .uri("${ApiPaths.API_V1_PUSH_TOKENS}/{deviceId}", testDeviceId)
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                document(
                    "push-token-delete",
                    pathParameters(
                        parameterWithName("deviceId").description("삭제할 디바이스 ID")
                    )
                )
            )

        verify(exactly = 1) { pushTokenService.deleteToken(testUserId, testDeviceId) }
    }

    @Test
    @DisplayName("모든 디바이스 푸시 토큰 삭제 성공")
    fun deleteAllTokens_Success() {
        // Given
        every { pushTokenService.deleteAllTokens(testUserId) } returns Mono.empty()

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .delete()
            .uri(ApiPaths.API_V1_PUSH_TOKENS)
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                document(
                    "push-token-delete-all"
                )
            )

        verify(exactly = 1) { pushTokenService.deleteAllTokens(testUserId) }
    }

    @Test
    @DisplayName("인증되지 않은 사용자의 푸시 토큰 등록 시 401 반환")
    fun registerToken_Unauthorized() {
        // Given
        val request = RegisterPushTokenRequest(
            token = testToken,
            deviceId = testDeviceId,
            deviceType = DeviceType.IOS,
            provider = PushProvider.EXPO
        )

        // When & Then
        webTestClient
            .post()
            .uri(ApiPaths.API_V1_PUSH_TOKENS)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    @DisplayName("빈 토큰으로 등록 시 400 반환")
    fun registerToken_InvalidRequest() {
        // Given
        val invalidRequest = mapOf(
            "token" to "",
            "deviceId" to testDeviceId,
            "deviceType" to "IOS",
            "provider" to "EXPO"
        )

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .post()
            .uri(ApiPaths.API_V1_PUSH_TOKENS)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().isBadRequest
    }
}
