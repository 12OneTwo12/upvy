package me.onetwo.growsnap.config

import io.mockk.mockk
import me.onetwo.growsnap.infrastructure.security.config.PublicApiPaths
import me.onetwo.growsnap.infrastructure.security.jwt.JwtTokenProvider
import me.onetwo.growsnap.infrastructure.security.jwt.UuidJwtAuthenticationToken
import me.onetwo.growsnap.infrastructure.security.oauth2.CustomReactiveOAuth2UserService
import me.onetwo.growsnap.infrastructure.security.oauth2.OAuth2AuthenticationFailureHandler
import me.onetwo.growsnap.infrastructure.security.oauth2.OAuth2AuthenticationSuccessHandler
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 테스트용 Security 설정
 *
 * Spring Security Test의 mockJwt()와 호환되는 설정입니다.
 *
 * ### 테스트 인증 방식
 * - `.mutateWith(mockJwt())` 사용 (Spring Security Test 표준)
 * - JWT claim에서 subject를 userId로 사용
 * - 실제 JWT 검증 로직은 mock으로 대체
 *
 * ### 사용 예시
 * ```kotlin
 * webTestClient
 *     .get()
 *     .uri("/api/v1/users/me")
 *     .mutateWith(mockJwt().jwt { jwt -> jwt.subject(userId.toString()) })
 *     .exchange()
 * ```
 */
@TestConfiguration
@EnableWebFluxSecurity
class TestSecurityConfig {

    /**
     * 테스트용 Mock JWT Decoder
     *
     * JWT 검증을 우회하고 모든 토큰을 유효하다고 간주합니다.
     */
    @Bean
    fun reactiveJwtDecoder(): ReactiveJwtDecoder {
        return ReactiveJwtDecoder { token ->
            Mono.error(IllegalStateException("JWT decoder should not be called in tests. Use mockJwt() instead."))
        }
    }

    /**
     * 테스트용 JWT Authentication Converter
     *
     * mockJwt()로 설정된 JWT를 UuidJwtAuthenticationToken으로 변환합니다.
     */
    @Bean
    fun jwtAuthenticationConverter(): Converter<Jwt, Mono<AbstractAuthenticationToken>> {
        return Converter { jwt ->
            val userId = UUID.fromString(jwt.subject)
            val authentication = UuidJwtAuthenticationToken(userId, jwt, emptyList())
            Mono.just(authentication)
        }
    }

    /**
     * 테스트용 Mock JwtTokenProvider
     *
     * OAuth2 로그인 성공 핸들러에서 사용하는 JwtTokenProvider를 mock으로 제공합니다.
     */
    @Bean
    fun jwtTokenProvider(): JwtTokenProvider = mockk(relaxed = true)

    /**
     * 테스트용 Mock OAuth2 서비스 및 핸들러
     */
    @Bean
    fun customReactiveOAuth2UserService(): CustomReactiveOAuth2UserService = mockk(relaxed = true)

    @Bean
    fun oAuth2AuthenticationSuccessHandler(): OAuth2AuthenticationSuccessHandler = mockk(relaxed = true)

    @Bean
    fun oAuth2AuthenticationFailureHandler(): OAuth2AuthenticationFailureHandler = mockk(relaxed = true)

    /**
     * 테스트용 Security 필터 체인
     *
     * OAuth2 Resource Server 설정으로 JWT 인증을 활성화합니다.
     */
    @Bean
    fun testSecurityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .authorizeExchange { authorize ->
                authorize
                    // 인증이 필요 없는 공개 API
                    .pathMatchers(*PublicApiPaths.AUTH_ENDPOINTS).permitAll()
                    // /me 엔드포인트는 명시적으로 인증 필요 (와일드카드 패턴보다 먼저 선언)
                    .pathMatchers("${ApiPaths.API_V1}/*/me").authenticated()
                    // 조회 전용 공개 API (GET 메서드만 허용)
                    .pathMatchers(PublicApiPaths.GetOnly.METHOD, *PublicApiPaths.GetOnly.PATHS).permitAll()
                    // 콘텐츠 개별 조회 (GET /api/v1/contents/{contentId})
                    .pathMatchers(PublicApiPaths.GetOnly.METHOD, *PublicApiPaths.GetOnly.CONTENT_PATHS).permitAll()
                    // 나머지는 JWT 인증 필요
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
            }
            .build()
    }
}
