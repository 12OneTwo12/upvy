package me.onetwo.growsnap.domain.auth.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.auth.dto.LogoutRequest
import me.onetwo.growsnap.domain.auth.dto.RefreshTokenRequest
import me.onetwo.growsnap.domain.auth.dto.RefreshTokenResponse
import me.onetwo.growsnap.domain.auth.service.AuthService
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * AuthController REST Docs 테스트
 */
@WebFluxTest(AuthController::class)
@AutoConfigureRestDocs
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("인증 컨트롤러 REST Docs 테스트")
class AuthControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var authService: AuthService

    @Test
    @DisplayName("Access Token 갱신 API 문서화")
    fun refreshToken() {
        // Given
        val request = RefreshTokenRequest(
            refreshToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        )

        val response = RefreshTokenResponse(
            accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ..."
        )

        every { authService.refreshAccessToken(request.refreshToken) } returns response

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith(
                document(
                    "auth/refresh-token",
                    requestFields(
                        fieldWithPath("refreshToken")
                            .description("JWT Refresh Token (14일 만료)")
                    ),
                    responseFields(
                        fieldWithPath("accessToken")
                            .description("새로 발급된 JWT Access Token (1시간 만료)")
                    )
                )
            )
    }

    @Test
    @DisplayName("Access Token 갱신 실패 - 유효하지 않은 토큰")
    fun refreshToken_InvalidToken() {
        // Given
        val request = RefreshTokenRequest(
            refreshToken = "invalid-token"
        )

        every {
            authService.refreshAccessToken(request.refreshToken)
        } throws IllegalArgumentException("유효하지 않은 Refresh Token입니다")

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("로그아웃 API 문서화")
    fun logout() {
        // Given
        val request = LogoutRequest(
            refreshToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
        )

        every { authService.logout(request.refreshToken) } returns Unit

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                document(
                    "auth/logout",
                    requestFields(
                        fieldWithPath("refreshToken")
                            .description("JWT Refresh Token (Redis에서 삭제됨)")
                    )
                )
            )
    }

    @Test
    @DisplayName("로그아웃 실패 - 유효하지 않은 토큰")
    fun logout_InvalidToken() {
        // Given
        val request = LogoutRequest(
            refreshToken = "invalid-token"
        )

        every {
            authService.logout(request.refreshToken)
        } throws IllegalArgumentException("유효하지 않은 Refresh Token입니다")

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("Access Token 갱신 실패 - Refresh Token 누락")
    fun refreshToken_MissingToken() {
        // Given
        val invalidRequest = mapOf("refreshToken" to "")

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("로그아웃 실패 - Refresh Token 누락")
    fun logout_MissingToken() {
        // Given
        val invalidRequest = mapOf("refreshToken" to "")

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().is4xxClientError
    }
}
