package me.onetwo.growsnap.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

/**
 * Testcontainers 기반 Redis 테스트 설정
 *
 * 실제 Redis 컨테이너를 사용하여 로컬과 CI 환경에서 동일한 테스트를 수행합니다.
 * Docker가 실행 중이어야 합니다.
 *
 * 장점:
 * - 실제 Redis 사용 (100% 호환성)
 * - 최신 Redis 버전 (7.x)
 * - 플랫폼 독립적 (M1/M2 Mac, Linux 모두 지원)
 * - 로컬과 CI 환경 통일
 * - Spring Boot 3.1+ @ServiceConnection 자동 설정
 */
@TestConfiguration(proxyBeanMethods = false)
class TestRedisConfig {

    /**
     * Redis Testcontainer 생성
     *
     * @ServiceConnection 어노테이션으로 Spring Boot가 자동으로 Redis 연결을 설정합니다.
     * Testcontainers가 동적으로 할당한 포트를 Spring Data Redis가 자동으로 사용합니다.
     */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> {
        logger.info("[TestRedisConfig] Creating Redis Testcontainer...")

        try {
            val container = GenericContainer(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(REDIS_PORT)
                .withReuse(true)  // 컨테이너 재사용으로 테스트 속도 향상
                .withLogConsumer(Slf4jLogConsumer(logger).withPrefix("REDIS"))

            logger.info("[TestRedisConfig] Redis container created successfully")
            logger.info("[TestRedisConfig] Container will start immediately via initMethod")

            return container
        } catch (e: Exception) {
            logger.error("[TestRedisConfig] Failed to create Redis container", e)
            logger.error("[TestRedisConfig] Make sure Docker is running!")
            throw e
        }
    }

    companion object {
        private const val REDIS_PORT = 6379
        private val logger = LoggerFactory.getLogger(TestRedisConfig::class.java)
    }
}
