package me.onetwo.upvy.infrastructure.security.jwt

import org.slf4j.LoggerFactory
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * JWT를 Authentication 객체로 변환하는 Reactive Converter
 *
 * OAuth2 Resource Server에서 JWT 토큰을 파싱한 후,
 * Authentication 객체로 변환합니다.
 *
 * ### 변환 규칙
 * - JWT의 subject를 UUID로 변환하여 principal로 설정
 * - JWT의 role claim을 GrantedAuthority로 변환
 * - @AuthenticationPrincipal userId: UUID로 컨트롤러에서 직접 사용 가능
 */
@Component
class JwtAuthenticationConverter : Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private val logger = LoggerFactory.getLogger(JwtAuthenticationConverter::class.java)

    /**
     * JWT를 Authentication 객체로 변환 (Reactive)
     *
     * @param jwt 파싱된 JWT 토큰
     * @return UUID를 principal로 갖는 UuidJwtAuthenticationToken
     */
    override fun convert(jwt: Jwt): Mono<AbstractAuthenticationToken> {
        return Mono.fromCallable {
            // JWT의 subject에서 UUID 추출
            val userId = try {
                UUID.fromString(jwt.subject)
            } catch (e: IllegalArgumentException) {
                logger.error("Invalid UUID in JWT subject: {}", jwt.subject)
                throw IllegalStateException("Invalid UUID in JWT subject: ${jwt.subject}", e)
            }

            // JWT의 role claim에서 권한 추출
            val authorities = extractAuthorities(jwt)

            // UUID를 principal로 갖는 커스텀 Authentication 토큰 생성
            UuidJwtAuthenticationToken(userId, jwt, authorities)
        }
    }

    /**
     * JWT에서 권한 정보 추출
     *
     * @param jwt JWT 토큰
     * @return GrantedAuthority 리스트
     */
    private fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val role = jwt.getClaimAsString("role") ?: return emptyList()
        return listOf(SimpleGrantedAuthority("ROLE_$role"))
    }
}

/**
 * UUID를 principal로 갖는 커스텀 Authentication 토큰
 *
 * @property principal UUID 형태의 사용자 ID
 * @property jwt 원본 JWT 토큰
 * @property authorities 권한 목록
 */
class UuidJwtAuthenticationToken(
    private val userId: UUID,
    private val jwt: Jwt,
    authorities: Collection<GrantedAuthority>
) : AbstractAuthenticationToken(authorities) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any = jwt

    override fun getPrincipal(): Any = userId

    override fun getName(): String = userId.toString()
}
