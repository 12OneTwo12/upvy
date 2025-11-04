package me.onetwo.growsnap.infrastructure.config

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Profile
import redis.embedded.RedisServer
import java.net.ServerSocket

/**
 * 테스트용 Embedded Redis 설정
 *
 * 테스트 실행 시 자동으로 Redis 서버를 시작하고 종료합니다.
 * 동적 포트 할당을 사용하여 포트 충돌을 방지하고 병렬 테스트 실행을 지원합니다.
 *
 * @TestConfiguration을 사용하여 테스트에서 자동으로 감지됩니다.
 * @Profile("test")를 사용하여 테스트 환경에서만 활성화됩니다.
 */
@TestConfiguration
@Profile("test")
class EmbeddedRedisConfig {

    private var redisServer: RedisServer? = null
    private var redisPort: Int = 0

    /**
     * 테스트 시작 시 Embedded Redis 서버 시작
     * 사용 가능한 포트를 동적으로 찾아서 Redis를 시작합니다.
     */
    @PostConstruct
    fun startRedis() {
        try {
            // 동적으로 사용 가능한 포트 찾기
            redisPort = findAvailablePort()

            redisServer = RedisServer.builder()
                .port(redisPort)
                .setting("maxmemory 128M")
                .setting("bind 127.0.0.1")  // 보안: 로컬에서만 접근 가능
                .build()
            redisServer?.start()

            // Spring이 사용할 Redis 포트를 시스템 프로퍼티로 설정
            System.setProperty("spring.data.redis.port", redisPort.toString())

            logger.info("✅ Embedded Redis started successfully on port {}", redisPort)
        } catch (e: Exception) {
            logger.error("❌ Failed to start Embedded Redis", e)
            throw IllegalStateException("Redis startup failed. Cannot proceed with tests.", e)
        }
    }

    /**
     * 테스트 종료 시 Embedded Redis 서버 종료
     */
    @PreDestroy
    fun stopRedis() {
        try {
            redisServer?.stop()
            logger.info("🛑 Embedded Redis stopped (port: {})", redisPort)
        } catch (e: Exception) {
            logger.warn("⚠️ Redis server failed to stop gracefully: {}", e.message)
        }
    }

    /**
     * 사용 가능한 포트를 찾습니다.
     * ServerSocket(0)을 사용하면 시스템이 사용 가능한 포트를 자동으로 할당합니다.
     */
    private fun findAvailablePort(): Int {
        return ServerSocket(0).use { it.localPort }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(EmbeddedRedisConfig::class.java)
    }
}
