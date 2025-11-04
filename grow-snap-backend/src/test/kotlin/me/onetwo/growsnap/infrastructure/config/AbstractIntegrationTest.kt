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
 * - 즉시 초기화: @DynamicPropertySource 호출 전에 컨테이너 시작 보장
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
         * 클래스 로드 시 즉시 초기화되어 @DynamicPropertySource에서 사용 가능합니다.
         *
         * GitHub Actions 환경에서의 안정성을 위해 lazy 대신 즉시 초기화 사용.
         */
        @JvmStatic
        val redisContainer: GenericContainer<*> = run {
            logger.info("Initializing Redis Testcontainer...")
            GenericContainer(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(REDIS_PORT)
                .withReuse(true)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(60))
                .also {
                    it.start()
                    logger.info("Redis container started successfully on {}:{}", it.host, it.getMappedPort(REDIS_PORT))
                }
        }

        /**
         * Redis 연결 정보를 Spring 설정에 동적으로 주입
         *
         * Testcontainers가 할당한 동적 포트를 Spring Boot에 전달합니다.
         * 컨테이너는 이미 시작된 상태이므로 즉시 host/port를 가져올 수 있습니다.
         */
        @JvmStatic
        @DynamicPropertySource
        fun registerRedisProperties(registry: DynamicPropertyRegistry) {
            val host = redisContainer.host
            val port = redisContainer.getMappedPort(REDIS_PORT)

            registry.add("spring.data.redis.host") { host }
            registry.add("spring.data.redis.port") { port }

            logger.info(
                "Redis connection properties registered: host={}, port={}",
                host,
                port
            )
        }
    }
}
