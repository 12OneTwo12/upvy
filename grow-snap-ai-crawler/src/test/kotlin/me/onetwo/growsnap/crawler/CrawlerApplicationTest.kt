package me.onetwo.growsnap.crawler

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Spring Boot 애플리케이션 컨텍스트 로드 테스트
 *
 * 모든 Bean이 정상적으로 생성되고 의존성 주입이 올바르게 되는지 검증합니다.
 * 이 테스트가 통과하면 애플리케이션이 정상적으로 구동될 수 있음을 보장합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("CrawlerApplication 컨텍스트 로드 테스트")
class CrawlerApplicationTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    @DisplayName("애플리케이션 컨텍스트가 정상적으로 로드된다")
    fun contextLoads() {
        assertThat(applicationContext).isNotNull
    }
}
