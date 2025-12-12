package me.onetwo.upvy.domain.auth.controller

import jakarta.validation.Valid
import me.onetwo.upvy.domain.auth.dto.EmailSigninRequest
import me.onetwo.upvy.domain.auth.dto.EmailSignupRequest
import me.onetwo.upvy.domain.auth.dto.EmailVerifyResponse
import me.onetwo.upvy.domain.auth.dto.LogoutRequest
import me.onetwo.upvy.domain.auth.dto.RefreshTokenRequest
import me.onetwo.upvy.domain.auth.dto.RefreshTokenResponse
import me.onetwo.upvy.domain.auth.service.AuthService
import me.onetwo.upvy.domain.user.service.UserService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.security.jwt.JwtTokenProvider
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
 * @property jwtTokenProvider JWT 토큰 Provider
 */
@RestController
@RequestMapping(ApiPaths.API_V1_AUTH)
class AuthController(
    private val authService: AuthService,
    private val userService: UserService,
    private val jwtTokenProvider: JwtTokenProvider
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
     * @param request 회원가입 요청 (이메일, 비밀번호, 이름)
     * @return 201 Created
     */
    @PostMapping("/email/signup")
    fun emailSignup(
        @Valid @RequestBody request: EmailSignupRequest
    ): Mono<ResponseEntity<Void>> {
        return authService.signup(request.email, request.password, request.name)
            .then(Mono.just(ResponseEntity.status(HttpStatus.CREATED).build<Void>()))
    }

    /**
     * 이메일 인증
     *
     * 이메일로 전송된 인증 토큰을 검증하고 이메일 인증을 완료합니다.
     * 인증 완료 후 자동으로 로그인 처리되어 JWT 토큰을 반환합니다.
     *
     * ### 사용 방법
     * - 프론트엔드: GET /auth/verify?token={token}
     * - 이메일 링크 클릭 시 자동으로 이 엔드포인트 호출
     *
     * @param token 인증 토큰 (query parameter)
     * @return EmailVerifyResponse JWT 토큰과 사용자 정보
     */
    @GetMapping("/email/verify")
    fun verifyEmail(
        @RequestParam token: String
    ): Mono<ResponseEntity<EmailVerifyResponse>> {
        return authService.verifyEmail(token)
            .flatMap { jwtTokens ->
                // JWT 토큰에서 사용자 ID 추출
                val userId = jwtTokenProvider.getUserIdFromToken(jwtTokens.accessToken)
                val email = jwtTokenProvider.getEmailFromToken(jwtTokens.accessToken)

                Mono.just(
                    ResponseEntity.ok(
                        EmailVerifyResponse(
                            accessToken = jwtTokens.accessToken,
                            refreshToken = jwtTokens.refreshToken,
                            userId = userId,
                            email = email
                        )
                    )
                )
            }
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
            .flatMap { jwtTokens ->
                // JWT 토큰에서 사용자 ID 추출
                val userId = jwtTokenProvider.getUserIdFromToken(jwtTokens.accessToken)
                val email = jwtTokenProvider.getEmailFromToken(jwtTokens.accessToken)

                Mono.just(
                    ResponseEntity.ok(
                        EmailVerifyResponse(
                            accessToken = jwtTokens.accessToken,
                            refreshToken = jwtTokens.refreshToken,
                            userId = userId,
                            email = email
                        )
                    )
                )
            }
    }
}
