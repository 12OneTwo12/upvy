package me.onetwo.growsnap.infrastructure.security.oauth2

import me.onetwo.growsnap.infrastructure.redis.RefreshTokenRepository
import me.onetwo.growsnap.infrastructure.security.jwt.JwtTokenProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.net.URI

/**
 * OAuth2 인증 성공 핸들러
 *
 * OAuth2 로그인 성공 시 JWT 토큰을 생성하고
 * 프론트엔드(웹) 또는 모바일 딥링크로 리다이렉트합니다.
 *
 * @property jwtTokenProvider JWT 토큰 생성기
 * @property refreshTokenRepository Refresh Token 저장소
 * @property frontendUrl 프론트엔드 URL (웹)
 * @property mobileDeeplinkUrl 모바일 딥링크 URL
 */
@Component
class OAuth2AuthenticationSuccessHandler(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    @Value("\${app.frontend-url:http://localhost:3000}")
    private val frontendUrl: String,
    @Value("\${app.mobile-deeplink-url:growsnap://oauth/callback}")
    private val mobileDeeplinkUrl: String
) : ServerAuthenticationSuccessHandler {

    /**
     * OAuth2 인증 성공 시 처리
     *
     * 1. CustomOAuth2User에서 사용자 정보 추출
     * 2. JWT Access Token과 Refresh Token 생성
     * 3. Refresh Token을 Redis에 저장
     * 4. state 파라미터로 플랫폼 구분 (웹 vs 모바일)
     * 5. 플랫폼에 따라 웹 프론트엔드 또는 모바일 딥링크로 리다이렉트
     *
     * @param webFilterExchange WebFilterExchange
     * @param authentication 인증 정보
     * @return Mono<Void>
     */
    override fun onAuthenticationSuccess(
        webFilterExchange: WebFilterExchange,
        authentication: Authentication
    ): Mono<Void> {
        val customOAuth2User = authentication.principal as CustomOAuth2User
        val exchange = webFilterExchange.exchange

        // JWT 토큰 생성
        val accessToken = jwtTokenProvider.generateAccessToken(
            userId = customOAuth2User.userId,
            email = customOAuth2User.email,
            role = customOAuth2User.role
        )

        val refreshToken = jwtTokenProvider.generateRefreshToken(customOAuth2User.userId)

        // Refresh Token을 Redis에 저장
        refreshTokenRepository.save(customOAuth2User.userId, refreshToken)

        // state 파라미터로 플랫폼 구분
        val state = exchange.request.queryParams.getFirst("state") ?: ""
        val isMobile = state.startsWith("mobile:")

        // 플랫폼에 따라 리다이렉트 URL 생성
        val redirectUrl = if (isMobile) {
            // 모바일: 딥링크로 리다이렉트
            UriComponentsBuilder.fromUriString(mobileDeeplinkUrl)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build()
                .toUriString()
        } else {
            // 웹: 기존 프론트엔드 URL로 리다이렉트
            UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/auth/oauth2/redirect")
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .queryParam("userId", customOAuth2User.userId.toString())
                .queryParam("email", customOAuth2User.email)
                .build()
                .toUriString()
        }

        exchange.response.statusCode = HttpStatus.FOUND
        exchange.response.headers.location = URI.create(redirectUrl)

        return exchange.response.setComplete()
    }
}
