package me.onetwo.upvy.domain.auth.controller

import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import me.onetwo.upvy.domain.auth.dto.LogoutRequest
import me.onetwo.upvy.domain.auth.dto.RefreshTokenRequest
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("인증 Controller 통합 테스트")
class AuthControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    @DisplayName("Access Token 갱신 실패 - 유효하지 않은 토큰")
    fun refreshToken_InvalidToken() {
        // Given: 유효하지 않은 토큰
        val request = RefreshTokenRequest(
            refreshToken = "invalid-token"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .post()
            .uri("${ApiPaths.API_V1_AUTH}/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("Access Token 갱신 실패 - Refresh Token 누락")
    fun refreshToken_MissingToken() {
        // Given: 빈 토큰
        val invalidRequest = mapOf("refreshToken" to "")

        // When & Then: API 호출 및 검증
        webTestClient
            .post()
            .uri("${ApiPaths.API_V1_AUTH}/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("로그아웃 실패 - 유효하지 않은 토큰")
    fun logout_InvalidToken() {
        // Given: 유효하지 않은 토큰
        val request = LogoutRequest(
            refreshToken = "invalid-token"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .post()
            .uri("${ApiPaths.API_V1_AUTH}/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("로그아웃 성공 - 빈 Request Body로 로그아웃")
    fun logout_WithEmptyBody() {
        // Given: 빈 요청
        val emptyRequest = LogoutRequest(refreshToken = "")

        // When & Then: API 호출 및 검증 - 빈 body로도 로그아웃 성공 (이미 로그아웃된 상태)
        webTestClient
            .post()
            .uri("${ApiPaths.API_V1_AUTH}/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(emptyRequest)
            .exchange()
            .expectStatus().isNoContent
    }
}
