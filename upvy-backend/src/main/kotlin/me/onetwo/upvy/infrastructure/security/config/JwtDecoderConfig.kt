package me.onetwo.upvy.infrastructure.security.config

import me.onetwo.upvy.infrastructure.security.jwt.JwtProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * JWT Decoder 설정
 *
 * OAuth2 Resource Server에서 사용할 JWT Decoder를 설정합니다.
 * Spring Security가 자동으로 JWT 토큰을 검증하고 인증 객체를 생성합니다.
 */
@Configuration
class JwtDecoderConfig(
    private val jwtProperties: JwtProperties
) {

    /**
     * ReactiveJwtDecoder 빈 생성
     *
     * HMAC SHA-256 알고리즘을 사용하여 JWT 토큰을 검증합니다.
     *
     * @return ReactiveJwtDecoder
     */
    @Bean
    fun reactiveJwtDecoder(): ReactiveJwtDecoder {
        val secretKey: SecretKey = SecretKeySpec(
            jwtProperties.secret.toByteArray(),
            "HmacSHA256"
        )

        return NimbusReactiveJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
    }

    /**
     * Apple Identity Token 검증을 위한 JwtDecoder 빈 생성
     *
     * Apple의 공개키(JWK Set)를 사용하여 Identity Token(JWT)을 검증합니다.
     * NimbusJwtDecoder는 내부적으로 JWK를 캐싱하므로 매 요청마다 Apple 서버로 네트워크 요청을 보내지 않습니다.
     *
     * @return JwtDecoder Apple Identity Token 검증용 디코더
     */
    @Bean
    fun appleJwtDecoder(): JwtDecoder {
        return NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys").build()
    }
}
