package me.onetwo.upvy.infrastructure.security.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import me.onetwo.upvy.domain.user.model.UserRole
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.SecretKey

/**
 * JWT 토큰 생성 및 검증을 담당하는 Provider
 *
 * Access Token과 Refresh Token을 생성하고, 토큰의 유효성을 검증합니다.
 *
 * @property jwtProperties JWT 설정 프로퍼티
 */
@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties
) {
    private val secretKey: SecretKey = Keys.hmacShaKeyFor(
        jwtProperties.secret.toByteArray(StandardCharsets.UTF_8)
    )

    /**
     * Access Token 생성
     *
     * @param userId 사용자 ID
     * @param email 사용자 이메일
     * @param role 사용자 역할
     * @return 생성된 JWT Access Token
     */
    fun generateAccessToken(userId: UUID, email: String, role: UserRole): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.accessTokenExpiration)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role.name)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()
    }

    /**
     * Refresh Token 생성
     *
     * @param userId 사용자 ID
     * @return 생성된 JWT Refresh Token
     */
    fun generateRefreshToken(userId: UUID): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.refreshTokenExpiration)

        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()
    }

    /**
     * 토큰에서 사용자 ID 추출
     *
     * @param token JWT 토큰
     * @return 사용자 ID
     * @throws io.jsonwebtoken.JwtException 토큰이 유효하지 않은 경우
     */
    fun getUserIdFromToken(token: String): UUID {
        val claims = getClaims(token)
        return UUID.fromString(claims.subject)
    }

    /**
     * 토큰에서 이메일 추출
     *
     * @param token JWT 토큰
     * @return 사용자 이메일
     * @throws io.jsonwebtoken.JwtException 토큰이 유효하지 않은 경우
     */
    fun getEmailFromToken(token: String): String {
        val claims = getClaims(token)
        return claims["email"] as String
    }

    /**
     * 토큰에서 역할 추출
     *
     * @param token JWT 토큰
     * @return 사용자 역할
     * @throws io.jsonwebtoken.JwtException 토큰이 유효하지 않은 경우
     */
    fun getRoleFromToken(token: String): UserRole {
        val claims = getClaims(token)
        val roleName = claims["role"] as String
        return UserRole.valueOf(roleName)
    }

    /**
     * 토큰 유효성 검증
     *
     * @param token JWT 토큰
     * @return 토큰이 유효하면 true, 그렇지 않으면 false
     */
    fun validateToken(token: String): Boolean {
        return try {
            getClaims(token)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 토큰에서 Claims 추출
     *
     * @param token JWT 토큰
     * @return JWT Claims
     * @throws io.jsonwebtoken.JwtException 토큰이 유효하지 않은 경우
     */
    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    /**
     * 토큰 만료 시간 가져오기
     *
     * @param token JWT 토큰
     * @return 만료 시간
     */
    fun getExpirationFromToken(token: String): Date {
        return getClaims(token).expiration
    }
}
