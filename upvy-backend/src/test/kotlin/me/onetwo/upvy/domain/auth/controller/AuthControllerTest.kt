package me.onetwo.upvy.domain.auth.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.auth.dto.AppleTokenRequest
import me.onetwo.upvy.domain.auth.dto.AppleTokenResponse
import me.onetwo.upvy.domain.auth.dto.AppleUserInfo
import me.onetwo.upvy.domain.auth.dto.ChangePasswordRequest
import me.onetwo.upvy.domain.auth.exception.InvalidAppleTokenException
import me.onetwo.upvy.domain.auth.dto.EmailSigninRequest
import me.onetwo.upvy.domain.auth.dto.EmailSignupRequest
import me.onetwo.upvy.domain.auth.dto.EmailVerifyCodeRequest
import me.onetwo.upvy.domain.auth.dto.EmailVerifyResponse
import me.onetwo.upvy.domain.auth.dto.LogoutRequest
import me.onetwo.upvy.domain.auth.dto.RefreshTokenRequest
import me.onetwo.upvy.domain.auth.dto.RefreshTokenResponse
import me.onetwo.upvy.domain.auth.dto.ResendVerificationCodeRequest
import me.onetwo.upvy.domain.auth.dto.ResetPasswordConfirmRequest
import me.onetwo.upvy.domain.auth.dto.ResetPasswordRequest
import me.onetwo.upvy.domain.auth.dto.ResetPasswordVerifyCodeRequest
import me.onetwo.upvy.domain.auth.service.AuthService
import me.onetwo.upvy.domain.user.service.UserService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.infrastructure.security.jwt.JwtTokenProvider
import me.onetwo.upvy.util.mockUser
import java.util.UUID
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
class AuthControllerTest : BaseReactiveTest() {

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
    @DisplayName("로그아웃 성공 - Refresh Token 없이 JWT로 로그아웃")
    fun logout_WithJwtOnly() {
        // Given
        val userId = UUID.randomUUID()

        every { authService.logoutByUserId(userId) } returns Unit

        // When & Then
        webTestClient
            .mutateWith(mockUser(userId))
            .post()
            .uri("${ApiPaths.API_V1_AUTH}/logout")
            .exchange()
            .expectStatus().isNoContent
    }

    @Test
    @DisplayName("이메일 회원가입 성공 시, 201 Created를 반환한다")
    fun emailSignup_Success_Returns201Created() {
        // Given
        val request = EmailSignupRequest(
            email = "newuser@example.com",
            password = "SecurePassword123!",
        )

        every { authService.signup(request.email, request.password, request.language) } returns Mono.empty()

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

    @Test
    @DisplayName("비밀번호 변경 성공")
    fun changePassword_Success() {
        // Given
        val userId = UUID.randomUUID()
        val request = ChangePasswordRequest(
            currentPassword = "old-password",
            newPassword = "new-password"
        )

        every { authService.changePassword(userId, request.currentPassword, request.newPassword) } returns Mono.empty()

        // When & Then
        webTestClient
            .mutateWith(mockUser(userId))
            .post()
            .uri("${ApiPaths.API_V1_AUTH}/password/change")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                document(
                    "auth/password-change",
                    requestFields(
                        fieldWithPath("currentPassword")
                            .description("현재 비밀번호"),
                        fieldWithPath("newPassword")
                            .description("새 비밀번호 (최소 8자 이상)")
                    )
                )
            )
    }

    @Test
    @DisplayName("비밀번호 재설정 요청 성공")
    fun resetPasswordRequest_Success() {
        // Given
        val request = ResetPasswordRequest(
            email = "user@example.com",
            language = "ko"
        )

        every { authService.resetPasswordRequest(request.email, request.language) } returns Mono.empty()

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/password/reset/request")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                document(
                    "auth/password-reset-request",
                    requestFields(
                        fieldWithPath("email")
                            .description("이메일 주소"),
                        fieldWithPath("language")
                            .description("이메일 언어 (ko: 한국어, en: 영어, ja: 일본어, 기본값: en)")
                    )
                )
            )
    }

    @Test
    @DisplayName("비밀번호 재설정 코드 검증 성공 (Step 1)")
    fun resetPasswordVerifyCode_Success() {
        // Given
        val request = ResetPasswordVerifyCodeRequest(
            email = "user@example.com",
            code = "123456"
        )

        every { authService.resetPasswordVerifyCode(request.email, request.code) } returns Mono.empty()

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/password/reset/verify-code")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                document(
                    "auth/password-reset-verify-code",
                    requestFields(
                        fieldWithPath("email")
                            .description("이메일 주소"),
                        fieldWithPath("code")
                            .description("6자리 인증 코드")
                    )
                )
            )
    }

    @Test
    @DisplayName("비밀번호 재설정 확정 성공 (Step 2)")
    fun resetPasswordConfirm_Success() {
        // Given
        val request = ResetPasswordConfirmRequest(
            email = "user@example.com",
            code = "123456",
            newPassword = "new-password"
        )

        every { authService.resetPasswordConfirm(request.email, request.code, request.newPassword) } returns Mono.empty()

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/password/reset/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                document(
                    "auth/password-reset-confirm",
                    requestFields(
                        fieldWithPath("email")
                            .description("이메일 주소"),
                        fieldWithPath("code")
                            .description("6자리 인증 코드"),
                        fieldWithPath("newPassword")
                            .description("새 비밀번호 (최소 8자 이상)")
                    )
                )
            )
    }

    @Test
    @DisplayName("Apple 네이티브 로그인 성공")
    fun authenticateWithApple_Success() {
        // Given
        val userId = UUID.randomUUID()
        val identityToken = "eyJraWQiOiJmaDZCczhDIiwiYWxnIjoiUlMyNTYifQ." +
            "eyJpc3MiOiJodHRwczovL2FwcGxlaWQuYXBwbGUuY29tIiwiYXVkIjoibWUub25ldHdvLnVwdnkiLCJleHAiOjE3MDg4NTg4M" +
            "DAsImlhdCI6MTcwODg1ODIwMCwic3ViIjoiMDAwMTIzLjQ1Njc4OWFiY2RlZjEyMzQuMTIzNCIsImVtYWlsIjoidXNlckB" +
            "leGFtcGxlLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlfQ..."
        val request = AppleTokenRequest(
            identityToken = identityToken,
            authorizationCode = "c1234567890abcdef",
            user = AppleUserInfo(
                familyName = "Doe",
                givenName = "John"
            )
        )

        val accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ..."
        val refreshToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJzdWIiOiIxMjM0NTY3ODkwIiwiaWF0IjoxNTE2MjM5MDIyfQ..."
        val response = AppleTokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            email = "user@example.com"
        )

        every {
            authService.authenticateWithApple(
                identityToken = request.identityToken,
                familyName = request.user?.familyName,
                givenName = request.user?.givenName
            )
        } returns Mono.just(response)

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/apple/token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .consumeWith(
                document(
                    "auth/apple-token",
                    requestFields(
                        fieldWithPath("identityToken")
                            .description("Apple Identity Token (JWT) - expo-apple-authentication에서 발급"),
                        fieldWithPath("authorizationCode")
                            .description("Apple Authorization Code (선택)").optional(),
                        fieldWithPath("user")
                            .description("사용자 이름 정보 (첫 로그인 시에만 제공됨, 선택)").optional(),
                        fieldWithPath("user.familyName")
                            .description("성 (선택)").optional(),
                        fieldWithPath("user.givenName")
                            .description("이름 (선택)").optional()
                    ),
                    responseFields(
                        fieldWithPath("accessToken")
                            .description("JWT Access Token (1시간 만료)"),
                        fieldWithPath("refreshToken")
                            .description("JWT Refresh Token (90일 만료)"),
                        fieldWithPath("userId")
                            .description("사용자 ID (UUID)"),
                        fieldWithPath("email")
                            .description("이메일 주소")
                    )
                )
            )
    }

    @Test
    @DisplayName("Apple 네이티브 로그인 실패 - 유효하지 않은 토큰")
    fun authenticateWithApple_InvalidToken() {
        // Given
        val request = AppleTokenRequest(
            identityToken = "invalid-token",
            authorizationCode = null,
            user = null
        )

        every {
            authService.authenticateWithApple(
                identityToken = request.identityToken,
                familyName = null,
                givenName = null
            )
        } returns Mono.error(InvalidAppleTokenException("Apple Identity Token이 유효하지 않습니다"))

        // When & Then
        webTestClient.post()
            .uri("${ApiPaths.API_V1_AUTH}/apple/token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isUnauthorized
    }
}
