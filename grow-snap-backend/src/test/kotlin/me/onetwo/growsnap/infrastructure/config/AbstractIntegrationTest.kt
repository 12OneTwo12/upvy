package me.onetwo.growsnap.infrastructure.config

import me.onetwo.growsnap.jooq.generated.tables.references.COMMENTS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENTS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_BLOCKS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_INTERACTIONS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_METADATA
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_PHOTOS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_SUBTITLES
import me.onetwo.growsnap.jooq.generated.tables.references.FOLLOWS
import me.onetwo.growsnap.jooq.generated.tables.references.REPORTS
import me.onetwo.growsnap.jooq.generated.tables.references.SEARCH_HISTORY
import me.onetwo.growsnap.jooq.generated.tables.references.USERS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_BLOCKS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_COMMENT_LIKES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_CONTENT_INTERACTIONS
import me.onetwo.growsnap.jooq.generated.tables.references.USER_LIKES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_PROFILES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_SAVES
import me.onetwo.growsnap.jooq.generated.tables.references.USER_STATUS_HISTORY
import me.onetwo.growsnap.jooq.generated.tables.references.USER_VIEW_HISTORY
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * 통합 테스트 베이스 클래스
 *
 * Redis와 MySQL Testcontainer를 static으로 관리하여 모든 통합 테스트에서 재사용합니다.
 * @DynamicPropertySource를 사용하여 동적 포트를 Spring 설정에 주입합니다.
 *
 * 베스트 프랙티스:
 * - static container: 모든 테스트에서 동일한 컨테이너 재사용 (성능 향상)
 * - @DynamicPropertySource: 동적 포트를 확실하게 전달
 * - companion object: Kotlin에서 static 멤버를 정의하는 방법
 * - 즉시 초기화: @DynamicPropertySource 호출 전에 컨테이너 시작 보장
 * - @BeforeEach: 각 테스트 전에 데이터베이스 정리 (테스트 간 격리)
 */
@Suppress("UtilityClassWithPublicConstructor")
abstract class AbstractIntegrationTest {

    @Autowired
    protected lateinit var dslContext: DSLContext

    /**
     * 각 테스트 실행 전에 데이터베이스의 모든 테이블 데이터를 정리합니다.
     *
     * 테스트 간 데이터 격리를 보장하여 테스트 실행 순서에 관계없이
     * 독립적인 테스트 환경을 제공합니다.
     *
     * 외래 키 순서를 고려하여 자식 테이블부터 삭제합니다.
     */
    @BeforeEach
    fun cleanupDatabase() {
        // 세션 타임존을 UTC로 설정 (DATEDIFF, CURDATE 등이 UTC 기준으로 동작)
        // 그 후 외래 키 제약 조건을 일시적으로 비활성화
        Mono.from(dslContext.query("SET time_zone = '+00:00'"))
            .then(Mono.from(dslContext.query("SET FOREIGN_KEY_CHECKS = 0")))
            // 관계 테이블 삭제 (자식 테이블)
            .then(Mono.from(dslContext.deleteFrom(SEARCH_HISTORY)))
            .then(Mono.from(dslContext.deleteFrom(REPORTS)))
            .then(Mono.from(dslContext.deleteFrom(USER_BLOCKS)))
            .then(Mono.from(dslContext.deleteFrom(CONTENT_BLOCKS)))
            .then(Mono.from(dslContext.deleteFrom(USER_COMMENT_LIKES)))
            .then(Mono.from(dslContext.deleteFrom(USER_CONTENT_INTERACTIONS)))
            .then(Mono.from(dslContext.deleteFrom(USER_LIKES)))
            .then(Mono.from(dslContext.deleteFrom(USER_SAVES)))
            .then(Mono.from(dslContext.deleteFrom(USER_VIEW_HISTORY)))
            .then(Mono.from(dslContext.deleteFrom(COMMENTS)))
            .then(Mono.from(dslContext.deleteFrom(CONTENT_SUBTITLES)))
            .then(Mono.from(dslContext.deleteFrom(CONTENT_INTERACTIONS)))
            .then(Mono.from(dslContext.deleteFrom(CONTENT_METADATA)))
            .then(Mono.from(dslContext.deleteFrom(CONTENT_PHOTOS)))
            .then(Mono.from(dslContext.deleteFrom(CONTENTS)))
            .then(Mono.from(dslContext.deleteFrom(FOLLOWS)))
            .then(Mono.from(dslContext.deleteFrom(USER_PROFILES)))
            .then(Mono.from(dslContext.deleteFrom(USER_STATUS_HISTORY)))
            .then(Mono.from(dslContext.deleteFrom(USERS)))
            // 외래 키 제약 조건 다시 활성화
            .then(Mono.from(dslContext.query("SET FOREIGN_KEY_CHECKS = 1")))
            .block()
    }

    companion object {
        private const val REDIS_PORT = 6379
        private const val MYSQL_PORT = 3306
        private val logger = LoggerFactory.getLogger(AbstractIntegrationTest::class.java)

        /**
         * MySQL Testcontainer (모든 테스트에서 공유)
         *
         * 프로덕션과 동일한 MySQL 환경에서 테스트합니다.
         * H2 대신 MySQL을 사용하여 시간 기반 Decay 등 MySQL 전용 기능을 테스트할 수 있습니다.
         */
        @JvmStatic
        val mysqlContainer: MySQLContainer<*> = run {
            logger.info("Initializing MySQL Testcontainer...")
            MySQLContainer(DockerImageName.parse("mysql:8.0"))
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test")
                .withInitScript("sql/schema-test.sql")
                .withEnv("TZ", "UTC")  // 컨테이너 OS 타임존
                .withCommand("--default-time-zone=+00:00")  // MySQL 서버 타임존 - DATEDIFF, CURDATE 등이 UTC 기준
                .withReuse(false)  // 타임존 설정 적용을 위해 재사용 비활성화
                .withStartupTimeout(Duration.ofSeconds(120))
                .also {
                    it.start()
                    logger.info("MySQL container started successfully on {}:{}", it.host, it.getMappedPort(MYSQL_PORT))
                }
        }

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
         * MySQL 및 Redis 연결 정보를 Spring 설정에 동적으로 주입
         */
        @JvmStatic
        @DynamicPropertySource
        fun registerContainerProperties(registry: DynamicPropertyRegistry) {
            // MySQL R2DBC URL 구성 (serverZoneId=UTC로 타임존 설정)
            val mysqlHost = mysqlContainer.host
            val mysqlPort = mysqlContainer.getMappedPort(MYSQL_PORT)
            val r2dbcUrl = "r2dbc:mysql://$mysqlHost:$mysqlPort/testdb?serverZoneId=UTC"

            registry.add("spring.r2dbc.url") { r2dbcUrl }
            registry.add("spring.r2dbc.username") { "test" }
            registry.add("spring.r2dbc.password") { "test" }

            logger.info("MySQL R2DBC connection registered: {}", r2dbcUrl)

            // Redis 연결 설정
            val redisHost = redisContainer.host
            val redisPort = redisContainer.getMappedPort(REDIS_PORT)

            registry.add("spring.data.redis.host") { redisHost }
            registry.add("spring.data.redis.port") { redisPort }

            logger.info("Redis connection registered: {}:{}", redisHost, redisPort)
        }
    }
}
