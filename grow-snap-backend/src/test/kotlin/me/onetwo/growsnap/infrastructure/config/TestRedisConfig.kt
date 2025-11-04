package me.onetwo.growsnap.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration


/**
 * Testcontainers 기반 Redis 테스트 설정
 *
 * Spring Boot 3.1+의 @ServiceConnection을 사용하여 자동으로 Redis 연결을 구성합니다.
 * 이 방식은 수동으로 RedisConnectionFactory를 만드는 것보다 간단하고 안정적입니다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestRedisConfig {

    companion object {
        private const val REDIS_PORT = 6379
        private val logger = LoggerFactory.getLogger(TestRedisConfig::class.java)
    }

    /**
     * Redis Testcontainer를 생성하고 @ServiceConnection으로 자동 연결
     *
     * @ServiceConnection 어노테이션이 자동으로 RedisConnectionDetails를 생성하여
     * Spring Boot가 올바른 호스트와 포트로 Redis를 구성하도록 합니다.
     */
    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> {
        val container = GenericContainer(DockerImageName.parse("redis:7.4.1-alpine3.20"))
            .withExposedPorts(REDIS_PORT)
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60))

        container.start()
        logger.info("Redis container started on {}:{}", container.host, container.getMappedPort(REDIS_PORT))

        return container
    }
}
