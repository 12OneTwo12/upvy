package me.onetwo.upvy.infrastructure.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * JWT 설정 프로퍼티
 *
 * application.yml의 jwt 설정을 바인딩합니다.
 *
 * @property secret JWT 서명에 사용할 비밀키 (최소 256비트)
 * @property accessTokenExpiration Access Token 유효기간 (밀리초)
 * @property refreshTokenExpiration Refresh Token 유효기간 (밀리초)
 */
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpiration: Long,
    val refreshTokenExpiration: Long
)
