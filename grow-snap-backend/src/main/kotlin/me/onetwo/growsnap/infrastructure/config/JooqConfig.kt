package me.onetwo.growsnap.infrastructure.config

import org.jooq.SQLDialect
import org.jooq.conf.RenderNameCase
import org.jooq.conf.RenderQuotedNames
import org.jooq.conf.Settings
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultDSLContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import javax.sql.DataSource

/**
 * JOOQ 설정
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
            .withRenderSchema(false) // 스키마 이름을 렌더링하지 않음 (PUBLIC 제거)
            .withRenderNameCase(RenderNameCase.LOWER) // 소문자로 렌더링
            .withRenderQuotedNames(RenderQuotedNames.ALWAYS) // 백틱으로 감싸기
    }

    /**
     * JOOQ Configuration 설정
     */
    @Bean
    fun jooqConfiguration(dataSource: DataSource, settings: Settings): DefaultConfiguration {
        val configuration = DefaultConfiguration()
        configuration.set(SQLDialect.MYSQL)
        configuration.set(DataSourceConnectionProvider(TransactionAwareDataSourceProxy(dataSource)))
        configuration.set(settings)
        return configuration
    }

    /**
     * JOOQ DSLContext 설정
     */
    @Bean
    fun dslContext(configuration: DefaultConfiguration): DefaultDSLContext {
        return DefaultDSLContext(configuration)
    }
}
