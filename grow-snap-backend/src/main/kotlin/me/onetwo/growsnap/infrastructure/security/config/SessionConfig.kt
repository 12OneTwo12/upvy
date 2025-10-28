package me.onetwo.growsnap.infrastructure.security.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession
import org.springframework.web.server.session.CookieWebSessionIdResolver
import org.springframework.web.server.session.WebSessionIdResolver

/**
 * Spring Session 설정
 *
 * **세션을 사용하는 이유:**
 * GrowSnap은 JWT 기반 인증을 사용하지만, OAuth2 로그인 플로우에서는
 * CSRF 방지 및 state 검증을 위해 일시적으로 세션이 필요합니다.
 *
 * ### 인증 플로우 2단계:
 *
 * **1단계: OAuth2 로그인 (세션 사용, 일시적)**
 * 1. 사용자가 /oauth2/authorization/google 접속
 * 2. Spring Security가 OAuth2AuthorizationRequest를 생성하고 WebSession에 저장 (CSRF 방지)
 * 3. 사용자를 Google 로그인 페이지로 리다이렉트
 * 4. Google이 사용자를 /login/oauth2/code/google로 리다이렉트
 * 5. Spring Security가 WebSession에서 OAuth2AuthorizationRequest를 조회하여 검증
 * 6. 검증 성공 시 JWT 토큰 생성 및 발급
 *
 * **2단계: API 요청 (JWT 사용, 세션 불필요)**
 * - 이후 모든 API 요청은 JWT 토큰만으로 인증
 * - 세션은 OAuth2 로그인 완료 후 더 이상 사용되지 않음
 *
 * @see EnableRedisWebSession Redis 기반 WebSession 활성화
 */
@Configuration
@EnableRedisWebSession
class SessionConfig {

    /**
     * WebSession ID Resolver 설정
     *
     * 쿠키 기반으로 세션 ID를 저장/조회합니다.
     * OAuth2 로그인 플로우에서 세션을 유지하는 데 사용됩니다.
     *
     * @return WebSessionIdResolver 쿠키 기반 세션 ID Resolver
     */
    @Bean
    fun webSessionIdResolver(): WebSessionIdResolver {
        val resolver = CookieWebSessionIdResolver()
        resolver.cookieName = "SESSION"
        return resolver
    }
}
