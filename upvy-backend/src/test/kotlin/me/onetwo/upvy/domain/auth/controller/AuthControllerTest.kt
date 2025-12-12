package me.onetwo.upvy.domain.auth.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.auth.dto.EmailSigninRequest
import me.onetwo.upvy.domain.auth.dto.EmailSignupRequest
import me.onetwo.upvy.domain.auth.dto.EmailVerifyCodeRequest
import me.onetwo.upvy.domain.auth.dto.EmailVerifyResponse
import me.onetwo.upvy.domain.auth.dto.LogoutRequest
import me.onetwo.upvy.domain.auth.dto.RefreshTokenRequest
import me.onetwo.upvy.domain.auth.dto.RefreshTokenResponse
import me.onetwo.upvy.domain.auth.dto.ResendVerificationCodeRequest
import me.onetwo.upvy.domain.auth.service.AuthService
import me.onetwo.upvy.domain.user.service.UserService
import me.onetwo.upvy.infrastructure.security.jwt.JwtTokenDto
import me.onetwo.upvy.infrastructure.security.jwt.JwtTokenProvider
import java.util.UUID
import me.onetwo.upvy.infrastructure.common.ApiPaths
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
import reactor.core.publisher.Mono

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

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var jwtTokenProvider: JwtTokenProvider

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

        every { authService.refreshAccessToken(request.refreshToken) } returns Mono.just(response)

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
        } returns Mono.error(IllegalArgumentException("유효하지 않은 Refresh Token입니다"))

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

    @Test
    @DisplayName("이메일 회원가입 성공 시, 201 Created를 반환한다")
    fun emailSignup_Success_Returns201Created() {
        // Given
        val request = EmailSignupRequest(
            email = "newuser@example.com",
            password = "SecurePassword123!",
            name = "홍길동"
        )

        every { authService.signup(request.email, request.password, request.name, request.language) } returns Mono.empty()

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/email/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .consumeWith(
                document(
                    "auth/email-signup",
                    requestFields(
                        fieldWithPath("email")
                            .description("이메일 주소 (고유, 인증 필요)"),
                        fieldWithPath("password")
                            .description("비밀번호 (평문, 서버에서 BCrypt 암호화)"),
                        fieldWithPath("name")
                            .description("사용자 이름 (선택, 프로필 생성 시 사용)").optional(),
                        fieldWithPath("language")
                            .description("사용자 언어 설정 (ko: 한국어, en: 영어, ja: 일본어, 기본값: en)").optional()
                    )
                )
            )
    }

    @Test
    @DisplayName("이메일 인증 코드 검증 성공 시, JWT 토큰을 반환한다")
    fun verifyEmailCode_Success_ReturnsJwtToken() {
        // Given
        val request = EmailVerifyCodeRequest(
            email = "user@example.com",
            code = "123456"
        )

        val userId = UUID.randomUUID()
        val accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.access..."
        val refreshToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.refresh..."

        val emailVerifyResponse = EmailVerifyResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            email = request.email
        )

        every { authService.verifyEmailCode(request.email, request.code) } returns Mono.just(emailVerifyResponse)

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/email/verify-code")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith(
                document(
                    "auth/email-verify-code",
                    requestFields(
                        fieldWithPath("email")
                            .description("이메일 주소"),
                        fieldWithPath("code")
                            .description("6자리 인증 코드")
                    ),
                    responseFields(
                        fieldWithPath("accessToken")
                            .description("JWT Access Token (1시간 만료)"),
                        fieldWithPath("refreshToken")
                            .description("JWT Refresh Token (14일 만료)"),
                        fieldWithPath("userId")
                            .description("사용자 UUID"),
                        fieldWithPath("email")
                            .description("인증된 이메일 주소")
                    )
                )
            )
    }

    @Test
    @DisplayName("인증 코드 재전송 성공 시, 204 No Content를 반환한다")
    fun resendVerificationCode_Success_Returns204NoContent() {
        // Given
        val request = ResendVerificationCodeRequest(
            email = "user@example.com"
        )

        every { authService.resendVerificationCode(request.email, request.language) } returns Mono.empty()

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/email/resend-code")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                document(
                    "auth/email-resend-code",
                    requestFields(
                        fieldWithPath("email")
                            .description("이메일 주소"),
                        fieldWithPath("language")
                            .description("이메일 언어 (ko: 한국어, en: 영어, ja: 일본어, 기본값: en)").optional()
                    )
                )
            )
    }

    @Test
    @DisplayName("이메일 로그인 성공 시, JWT 토큰을 반환한다")
    fun emailSignin_Success_ReturnsJwtToken() {
        // Given
        val request = EmailSigninRequest(
            email = "user@example.com",
            password = "SecurePassword123!"
        )

        val userId = UUID.randomUUID()
        val accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.access..."
        val refreshToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.refresh..."

        val emailVerifyResponse = EmailVerifyResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            email = request.email
        )

        every { authService.signIn(request.email, request.password) } returns Mono.just(emailVerifyResponse)

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/email/signin")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith(
                document(
                    "auth/email-signin",
                    requestFields(
                        fieldWithPath("email")
                            .description("이메일 주소"),
                        fieldWithPath("password")
                            .description("비밀번호")
                    ),
                    responseFields(
                        fieldWithPath("accessToken")
                            .description("JWT Access Token (1시간 만료)"),
                        fieldWithPath("refreshToken")
                            .description("JWT Refresh Token (14일 만료)"),
                        fieldWithPath("userId")
                            .description("사용자 UUID"),
                        fieldWithPath("email")
                            .description("로그인한 이메일 주소")
                    )
                )
            )
    }
}
