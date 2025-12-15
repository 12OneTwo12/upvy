package me.onetwo.upvy.infrastructure.security.oauth2

import me.onetwo.upvy.infrastructure.redis.RefreshTokenRepository
import me.onetwo.upvy.infrastructure.security.jwt.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
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
    @Value("\${app.mobile-deeplink-url:upvy://oauth/callback}")
    private val mobileDeeplinkUrl: String
) : ServerAuthenticationSuccessHandler {

    private val logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler::class.java)

    /**
     * OAuth2 인증 성공 시 처리
     *
     * 1. CustomOAuth2User에서 사용자 정보 추출
     * 2. JWT Access Token과 Refresh Token 생성
     * 3. Refresh Token을 Redis에 저장
     * 4. WebSession에서 플랫폼 구분 (웹 vs 모바일)
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

        // Refresh Token을 Redis에 저장 (blocking 코드를 별도 스레드에서 실행)
        return Mono.fromRunnable<Void> {
                try {
                    refreshTokenRepository.save(customOAuth2User.userId, refreshToken)
                } catch (error: Exception) {
                    // Redis 저장 실패 시에도 로그인 진행 (리프레시 토큰 없이)
                    logger.error("Failed to save refresh token to Redis for user: ${customOAuth2User.userId}", error)
                }
            }
            .subscribeOn(Schedulers.boundedElastic()) // blocking 작업을 별도 스레드풀에서 실행
            .then(exchange.session)
            .flatMap { session ->
                // 플랫폼 구분: WebSession에서 isMobile 플래그 확인
                val isMobile = session.attributes[OAuth2AuthorizationRequestCustomizer.SESSION_ATTR_IS_MOBILE] as? Boolean ?: false

                logger.info("OAuth2 success - userId: {}, email: {}, isMobile: {}",
                    customOAuth2User.userId, customOAuth2User.email, isMobile)

                // 플랫폼에 따라 리다이렉트 URL 생성
                val redirectUrl = if (isMobile) {
                    // 모바일: 딥링크로 리다이렉트
                    UriComponentsBuilder.fromUriString(mobileDeeplinkUrl)
                        .queryParam("accessToken", accessToken)
                        .queryParam("refreshToken", refreshToken)
                        .queryParam("userId", customOAuth2User.userId.toString())
                        .queryParam("email", customOAuth2User.email)
                        .build()
                        .toUriString()
                } else {
                    // 웹: 프론트엔드 URL로 리다이렉트
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

                exchange.response.setComplete()
            }
    }
}
