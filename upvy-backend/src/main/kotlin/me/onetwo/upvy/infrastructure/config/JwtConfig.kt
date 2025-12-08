package me.onetwo.upvy.infrastructure.config

import me.onetwo.upvy.infrastructure.security.jwt.JwtProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * JWT 설정 클래스
 *
 * JwtProperties를 활성화하여 application.yml의 jwt 설정을 바인딩합니다.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfig
