package me.onetwo.growsnap.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * 통합 테스트 베이스 클래스
 *
 * Redis Testcontainer를 static으로 관리하여 모든 통합 테스트에서 재사용합니다.
 * @DynamicPropertySource를 사용하여 동적 포트를 Spring 설정에 주입합니다.
 *
 * 베스트 프랙티스:
 * - static container: 모든 테스트에서 동일한 컨테이너 재사용 (성능 향상)
 * - @DynamicPropertySource: 동적 포트를 확실하게 전달
 * - companion object: Kotlin에서 static 멤버를 정의하는 방법
 */
@Suppress("UtilityClassWithPublicConstructor")
abstract class AbstractIntegrationTest {

    companion object {
        private const val REDIS_PORT = 6379
        private val logger = LoggerFactory.getLogger(AbstractIntegrationTest::class.java)

        /**
         * Redis Testcontainer (모든 테스트에서 공유)
         *
         * static으로 정의하여 테스트 클래스 간에 재사용됩니다.
         * 첫 번째 테스트 실행 시 한 번만 시작되고, 모든 테스트가 끝날 때 종료됩니다.
         *
         * lazy를 사용하여 @DynamicPropertySource가 실행되기 전에 확실히 초기화됩니다.
         */
        @JvmStatic
        val redisContainer: GenericContainer<*> by lazy {
            GenericContainer(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(REDIS_PORT)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(60))
                .apply {
                    start()
                    logger.info("Redis container started on {}:{}", host, getMappedPort(REDIS_PORT))
                }
        }

        /**
         * Redis 연결 정보를 Spring 설정에 동적으로 주입
         *
         * Testcontainers가 할당한 동적 포트를 Spring Boot에 전달합니다.
         * 이 방법은 @ServiceConnection보다 더 명시적이고 안정적입니다.
         */
        @JvmStatic
        @DynamicPropertySource
        fun registerRedisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(REDIS_PORT) }
            logger.info(
                "Redis connection properties registered: host={}, port={}",
                redisContainer.host,
                redisContainer.getMappedPort(REDIS_PORT)
            )
        }
    }
}
