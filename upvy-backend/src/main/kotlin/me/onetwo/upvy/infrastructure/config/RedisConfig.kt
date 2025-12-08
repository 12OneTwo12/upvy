package me.onetwo.upvy.infrastructure.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis 설정 클래스
 *
 * Redis 연결 및 RedisTemplate을 설정합니다.
 * ReactiveRedisTemplate은 Spring Boot의 자동 설정을 사용합니다.
 */
@Configuration
class RedisConfig {

    @Value("\${spring.data.redis.host}")
    private lateinit var host: String

    @Value("\${spring.data.redis.port}")
    private var port: Int = 6379

    /**
     * Lettuce Connection Factory 빈 생성
     *
     * LettuceConnectionFactory는 RedisConnectionFactory와 ReactiveRedisConnectionFactory를 모두 구현합니다.
     * 따라서 하나의 빈으로 Non-Reactive와 Reactive 모두 지원합니다.
     *
     * Spring이 빈 라이프사이클을 관리하므로 afterPropertiesSet()을 수동으로 호출할 필요가 없습니다.
     *
     * @return Lettuce Connection Factory
     */
    @Bean
    fun lettuceConnectionFactory(): LettuceConnectionFactory {
        return LettuceConnectionFactory(host, port)
    }

    /**
     * RedisTemplate 빈 생성
     *
     * Non-Reactive Redis 작업을 위한 템플릿입니다.
     *
     * @param lettuceConnectionFactory Lettuce Connection Factory
     * @return RedisTemplate<String, String>
     */
    @Bean
    fun redisTemplate(lettuceConnectionFactory: LettuceConnectionFactory): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()
        template.connectionFactory = lettuceConnectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = StringRedisSerializer()
        return template
    }
}
