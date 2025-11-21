package me.onetwo.growsnap.infrastructure.security.oauth2

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
 */
@Component
class OAuth2AuthorizationRequestCustomizer(
    clientRegistrationRepository: ReactiveClientRegistrationRepository
) : ServerOAuth2AuthorizationRequestResolver {

    private val defaultResolver = DefaultServerOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository
    )

    override fun resolve(exchange: ServerWebExchange): Mono<OAuth2AuthorizationRequest> {
        return defaultResolver.resolve(exchange)
            .map { customizeAuthorizationRequest(it) }
    }

    override fun resolve(
        exchange: ServerWebExchange,
        clientRegistrationId: String
    ): Mono<OAuth2AuthorizationRequest> {
        return defaultResolver.resolve(exchange, clientRegistrationId)
            .map { customizeAuthorizationRequest(it) }
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
