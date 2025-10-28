package me.onetwo.growsnap.infrastructure.security.oauth2

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.server.WebFilterExchange
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.net.URI

/**
 * OAuth2 인증 실패 핸들러
 *
 * OAuth2 로그인 실패 시 에러 정보를 포함하여
 * 프론트엔드(웹) 또는 모바일 딥링크로 리다이렉트합니다.
 *
 * @property frontendUrl 프론트엔드 URL (웹)
 * @property mobileDeeplinkUrl 모바일 딥링크 URL
 */
@Component
class OAuth2AuthenticationFailureHandler(
    @Value("\${app.frontend-url:http://localhost:3000}")
    private val frontendUrl: String,
    @Value("\${app.mobile-deeplink-url:growsnap://oauth/callback}")
    private val mobileDeeplinkUrl: String
) : ServerAuthenticationFailureHandler {

    /**
     * OAuth2 인증 실패 시 처리
     *
     * 1. state 파라미터로 플랫폼 구분 (웹 vs 모바일)
     * 2. 에러 정보를 쿼리 파라미터로 포함
     * 3. 플랫폼에 따라 웹 프론트엔드 또는 모바일 딥링크로 리다이렉트
     *
     * @param webFilterExchange WebFilterExchange
     * @param exception 인증 예외
     * @return Mono<Void>
     */
    override fun onAuthenticationFailure(
        webFilterExchange: WebFilterExchange,
        exception: AuthenticationException
    ): Mono<Void> {
        val exchange = webFilterExchange.exchange
        val errorMessage = exception.message ?: "인증에 실패했습니다"

        // state 파라미터로 플랫폼 구분
        val state = exchange.request.queryParams.getFirst("state") ?: ""
        val isMobile = state.startsWith("mobile:")

        // 플랫폼에 따라 리다이렉트 URL 생성
        val redirectUri = if (isMobile) {
            // 모바일: 딥링크로 리다이렉트
            UriComponentsBuilder.fromUriString(mobileDeeplinkUrl)
                .queryParam("error", errorMessage)
                .build()
                .encode()
                .toUri()
        } else {
            // 웹: 기존 프론트엔드 에러 페이지로 리다이렉트
            UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/auth/oauth2/error")
                .queryParam("error", errorMessage)
                .build()
                .encode()
                .toUri()
        }

        exchange.response.statusCode = HttpStatus.FOUND
        exchange.response.headers.location = redirectUri

        return exchange.response.setComplete()
    }
}
