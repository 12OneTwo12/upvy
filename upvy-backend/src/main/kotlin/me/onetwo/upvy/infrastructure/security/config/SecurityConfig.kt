package me.onetwo.upvy.infrastructure.security.config

import me.onetwo.upvy.infrastructure.security.jwt.JwtAuthenticationConverter
import me.onetwo.upvy.infrastructure.security.oauth2.CustomReactiveOAuth2UserService
import me.onetwo.upvy.infrastructure.security.oauth2.OAuth2AuthenticationFailureHandler
import me.onetwo.upvy.infrastructure.security.oauth2.OAuth2AuthenticationSuccessHandler
import me.onetwo.upvy.infrastructure.security.oauth2.OAuth2AuthorizationRequestCustomizer
import me.onetwo.upvy.infrastructure.common.ApiPaths
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.client.authentication.OAuth2LoginReactiveAuthenticationManager
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveAuthorizationCodeTokenResponseClient
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

/**
 * Spring Security 설정
 *
 * OAuth2 로그인, OAuth2 Resource Server (JWT 인증), CORS, 인증/인가 규칙을 설정합니다.
 *
 * ### 인증 방식
 * 1. OAuth2 소셜 로그인 (Google/Naver/Kakao) - 최초 로그인
 * 2. OAuth2 Resource Server (JWT) - API 요청 시 JWT 토큰 검증
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val customOAuth2UserService: CustomReactiveOAuth2UserService,
    private val oAuth2SuccessHandler: OAuth2AuthenticationSuccessHandler,
    private val oAuth2FailureHandler: OAuth2AuthenticationFailureHandler,
    private val jwtAuthenticationConverter: JwtAuthenticationConverter,
    private val oauth2AuthorizationRequestCustomizer: OAuth2AuthorizationRequestCustomizer
) {

    /**
     * Spring Security 필터 체인 설정
     *
     * OAuth2 로그인, OAuth2 Resource Server (JWT), CORS, CSRF, 인증/인가 규칙을 구성합니다.
     *
     * ### OAuth2 Resource Server
     * - JWT 토큰을 자동으로 검증하고 인증 객체를 생성합니다.
     * - ReactiveJwtDecoder가 JWT 토큰을 파싱하고 검증합니다.
     * - JwtAuthenticationConverter가 JWT의 subject를 UUID로 변환하여 principal로 설정합니다.
     */
    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeExchange { authorize ->
                authorize
                    // 비밀번호 변경 요청은 명시적으로 인증 필요 (와일드카드 패턴보다 먼저 선언)
                    .pathMatchers("${ApiPaths.API_V1_AUTH}/password/change").authenticated()
                    // 인증이 필요 없는 공개 API
                    .pathMatchers(*PublicApiPaths.AUTH_ENDPOINTS).permitAll()
                    // /me 엔드포인트는 명시적으로 인증 필요 (와일드카드 패턴보다 먼저 선언)
                    .pathMatchers("${ApiPaths.API_V1}/*/me").authenticated()
                    // 조회 전용 공개 API (GET 메서드만 허용)
                    .pathMatchers(PublicApiPaths.GetOnly.METHOD, *PublicApiPaths.GetOnly.PATHS).permitAll()
                    // 콘텐츠 개별 조회 (GET /api/v1/contents/{contentId})
                    .pathMatchers(PublicApiPaths.GetOnly.METHOD, *PublicApiPaths.GetOnly.CONTENT_PATHS).permitAll()
                    // 나머지는 인증 필요
                    .anyExchange().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .authorizationRequestResolver(oauth2AuthorizationRequestCustomizer)
                    .authenticationSuccessHandler(oAuth2SuccessHandler)
                    .authenticationFailureHandler(oAuth2FailureHandler)
                    .authenticationManager(oauth2AuthenticationManager())
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
                }
            }
            .build()
    }

    @Bean
    fun oauth2AuthenticationManager(): ReactiveAuthenticationManager {
        val tokenResponseClient = WebClientReactiveAuthorizationCodeTokenResponseClient()
        return OAuth2LoginReactiveAuthenticationManager(tokenResponseClient, customOAuth2UserService)
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf(
                "http://localhost:3000",
                "http://localhost:5173"
            )
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    /**
     * BCrypt 비밀번호 암호화
     *
     * 이메일 가입 사용자의 비밀번호를 안전하게 암호화합니다.
     */
    @Bean
    fun passwordEncoder(): BCryptPasswordEncoder {
        return BCryptPasswordEncoder()
    }
}
