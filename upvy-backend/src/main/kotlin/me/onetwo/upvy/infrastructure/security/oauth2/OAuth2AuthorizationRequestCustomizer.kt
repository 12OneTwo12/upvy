package me.onetwo.upvy.infrastructure.security.oauth2

import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * OAuth2 Authorization Request 커스터마이저
 *
 * OAuth2 인증 요청에 추가 파라미터를 설정합니다.
 * - prompt=select_account: 매번 구글 계정 선택 화면 표시
 * - WebSession에 isMobile 플래그 저장 (state 파라미터 기반)
 */
@Component
class OAuth2AuthorizationRequestCustomizer(
    clientRegistrationRepository: ReactiveClientRegistrationRepository
) : ServerOAuth2AuthorizationRequestResolver {

    companion object {
        const val SESSION_ATTR_IS_MOBILE = "oauth2.isMobile"
    }

    private val defaultResolver = DefaultServerOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository
    )

    override fun resolve(exchange: ServerWebExchange): Mono<OAuth2AuthorizationRequest> {
        return defaultResolver.resolve(exchange).customize(exchange)
    }

    override fun resolve(
        exchange: ServerWebExchange,
        clientRegistrationId: String
    ): Mono<OAuth2AuthorizationRequest> {
        return defaultResolver.resolve(exchange, clientRegistrationId).customize(exchange)
    }

    private fun Mono<OAuth2AuthorizationRequest>.customize(exchange: ServerWebExchange) =
        flatMap { authRequest ->
            saveIsMobileToSession(exchange).thenReturn(authRequest)
        }.map { customizeAuthorizationRequest(it) }

    private fun saveIsMobileToSession(exchange: ServerWebExchange): Mono<Void> {
        // 클라이언트가 보낸 state 파라미터 확인 (mobile:xxx 형식)
        val clientState = exchange.request.queryParams.getFirst("state")
        val isMobile = clientState?.startsWith("mobile:") == true

        // WebSession에 isMobile 플래그 저장 (OAuth2AuthenticationSuccessHandler에서 사용)
        return exchange.session.flatMap { session ->
            session.attributes[SESSION_ATTR_IS_MOBILE] = isMobile
            session.save()
        }
    }

    private fun customizeAuthorizationRequest(
        authorizationRequest: OAuth2AuthorizationRequest
    ): OAuth2AuthorizationRequest {
        // prompt=select_account 파라미터 추가 (구글 계정 선택 화면 강제 표시)
        val additionalParameters = authorizationRequest.additionalParameters.toMutableMap()
        additionalParameters["prompt"] = "select_account"

        return OAuth2AuthorizationRequest.from(authorizationRequest)
            .additionalParameters(additionalParameters)
            .build()
    }
}
