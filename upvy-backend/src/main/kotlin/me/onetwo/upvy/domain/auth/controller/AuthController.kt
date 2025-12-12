package me.onetwo.upvy.domain.auth.controller

import jakarta.validation.Valid
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
import me.onetwo.upvy.infrastructure.common.ApiPaths
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * 인증 컨트롤러
 *
 * OAuth2 로그인, 이메일 가입/로그인, 토큰 갱신, 로그아웃 등의 인증 관련 API를 제공합니다.
 *
 * OAuth2 로그인 시작 엔드포인트는 Spring Security가 자동으로 제공합니다:
 * - GET /oauth2/authorization/{registrationId} (예: /oauth2/authorization/google)
 *
 * @property authService 인증 서비스
 * @property userService 사용자 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1_AUTH)
class AuthController(
    private val authService: AuthService,
    private val userService: UserService
) {

    /**
     * Access Token 갱신
     *
     * Refresh Token을 사용하여 새로운 Access Token을 발급받습니다.
     *
     * @param request Refresh Token 요청
     * @return RefreshTokenResponse 새로운 Access Token
     */
    @PostMapping("/refresh")
    fun refreshToken(
        @Valid @RequestBody request: RefreshTokenRequest
    ): Mono<ResponseEntity<RefreshTokenResponse>> {
        return authService.refreshAccessToken(request.refreshToken)
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 로그아웃
     *
     * Refresh Token을 무효화하여 로그아웃 처리합니다.
     *
     * @param request 로그아웃 요청
     * @return ResponseEntity<Void>
     */
    @PostMapping("/logout")
    fun logout(
        @Valid @RequestBody request: LogoutRequest
    ): Mono<ResponseEntity<Void>> {
        return Mono.fromRunnable<Void> {
            authService.logout(request.refreshToken)
        }.then(Mono.just(ResponseEntity.noContent().build<Void>()))
    }

    /**
     * 이메일 회원가입
     *
     * 이메일 주소와 비밀번호로 회원가입합니다.
     * 회원가입 후 이메일 인증 메일이 발송되며, 인증 완료 전까지는 로그인할 수 없습니다.
     *
     * @param request 회원가입 요청 (이메일, 비밀번호, 이름, 언어)
     * @return 201 Created
     */
    @PostMapping("/email/signup")
    fun emailSignup(
        @Valid @RequestBody request: EmailSignupRequest
    ): Mono<ResponseEntity<Void>> {
        return authService.signup(request.email, request.password, request.name, request.language)
            .then(Mono.just(ResponseEntity.status(HttpStatus.CREATED).build<Void>()))
    }

    /**
     * 이메일 인증 코드 검증
     *
     * 이메일로 발송된 6자리 인증 코드를 검증합니다.
     * 인증 완료 후 자동으로 로그인 처리되어 JWT 토큰을 반환합니다.
     *
     * @param request 이메일 인증 코드 검증 요청 (이메일, 6자리 코드)
     * @return EmailVerifyResponse JWT 토큰과 사용자 정보
     */
    @PostMapping("/email/verify-code")
    fun verifyEmailCode(
        @Valid @RequestBody request: EmailVerifyCodeRequest
    ): Mono<ResponseEntity<EmailVerifyResponse>> {
        return authService.verifyEmailCode(request.email, request.code)
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 인증 코드 재전송
     *
     * 만료되었거나 받지 못한 인증 코드를 재전송합니다.
     * 1분에 1회만 재전송 가능합니다.
     *
     * @param request 인증 코드 재전송 요청 (이메일, 언어)
     * @return 204 No Content
     */
    @PostMapping("/email/resend-code")
    fun resendVerificationCode(
        @Valid @RequestBody request: ResendVerificationCodeRequest
    ): Mono<ResponseEntity<Void>> {
        return authService.resendVerificationCode(request.email, request.language)
            .then(Mono.just(ResponseEntity.noContent().build<Void>()))
    }

    /**
     * 이메일 로그인
     *
     * 이메일 주소와 비밀번호로 로그인합니다.
     *
     * @param request 로그인 요청 (이메일, 비밀번호)
     * @return EmailVerifyResponse JWT 토큰과 사용자 정보
     */
    @PostMapping("/email/signin")
    fun emailSignin(
        @Valid @RequestBody request: EmailSigninRequest
    ): Mono<ResponseEntity<EmailVerifyResponse>> {
        return authService.signIn(request.email, request.password)
            .map { response -> ResponseEntity.ok(response) }
    }
}
