package me.onetwo.upvy.infrastructure.security.config

import me.onetwo.upvy.infrastructure.security.jwt.JwtProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
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
}
