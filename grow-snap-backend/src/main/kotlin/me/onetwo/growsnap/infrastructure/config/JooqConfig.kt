package me.onetwo.growsnap.infrastructure.config

import io.r2dbc.spi.ConnectionFactory
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * JOOQ 설정 (R2DBC)
 *
 * JOOQ 3.17+는 R2DBC를 네이티브로 지원합니다.
 * ConnectionFactory를 사용하여 DSLContext를 생성하면,
 * JOOQ의 type-safe API로 reactive 쿼리를 실행할 수 있습니다.
 *
 * JOOQ가 생성하는 SQL에서 스키마 이름(PUBLIC)을 제거하도록 설정합니다.
 * MySQL은 H2와 달리 PUBLIC 스키마가 없으므로, 스키마를 렌더링하지 않아야 합니다.
 */
@Configuration
class JooqConfig {

    /**
     * JOOQ Settings 설정
     *
     * - renderSchema: false - SQL 렌더링 시 스키마 이름 제거
     * - renderNameCase: LOWER - 테이블/컬럼명을 소문자로 렌더링
     * - renderQuotedNames: ALWAYS - 백틱(`)으로 식별자를 항상 감싸기
     */
    @Bean
    fun jooqSettings(): Settings {
        return Settings()
            .withRenderSchema(false)
            .withRenderNameCase(RenderNameCase.LOWER)
            .withRenderQuotedNames(RenderQuotedNames.ALWAYS)
    }

    /**
     * JOOQ DSLContext 설정 (R2DBC)
     *
     * ConnectionFactory를 사용하여 R2DBC 기반 DSLContext를 생성합니다.
     * 이를 통해 JOOQ의 type-safe API로 reactive 쿼리를 실행할 수 있습니다.
     */
    @Bean
    fun dslContext(connectionFactory: ConnectionFactory, settings: Settings): DSLContext {
        return DSL.using(connectionFactory, SQLDialect.MYSQL, settings)
    }
}
