package me.onetwo.upvy.infrastructure.security.jwt

/**
 * JWT 토큰 응답 DTO
 *
 * Access Token과 Refresh Token을 함께 반환하는 DTO
 *
 * @property accessToken JWT Access Token
 * @property refreshToken JWT Refresh Token
 * @property tokenType 토큰 타입 (기본값: "Bearer")
 */
data class JwtTokenDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer"
)
