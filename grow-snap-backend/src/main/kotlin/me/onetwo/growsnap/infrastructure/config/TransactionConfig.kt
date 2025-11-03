package me.onetwo.growsnap.infrastructure.config

import io.r2dbc.spi.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.reactive.TransactionalOperator

/**
 * 트랜잭션 관리 설정 (R2DBC)
 *
 * WebFlux + R2DBC 완전한 Reactive 스택:
 * - ReactiveTransactionManager: Reactor Context 기반 트랜잭션 관리
 * - @Transactional: Mono/Flux 구독 시 트랜잭션 시작, 완료 시 커밋
 * - @TransactionalEventListener(AFTER_COMMIT): 트랜잭션 커밋 후 이벤트 발화 보장
 * - 완전한 Non-blocking 처리 (Schedulers.boundedElastic() 불필요)
 */
@Configuration
@EnableTransactionManagement
class TransactionConfig {

    /**
     * R2DBC Transaction Manager Bean
     *
     * R2DBC 환경에서 트랜잭션을 관리하기 위한 Bean입니다.
     * 이 Bean이 있어야 @TransactionalEventListener가 올바르게 작동합니다.
     */
    @Bean
    fun reactiveTransactionManager(connectionFactory: ConnectionFactory): ReactiveTransactionManager {
        return R2dbcTransactionManager(connectionFactory)
    }

    /**
     * TransactionalOperator Bean
     *
     * 프로그래밍 방식으로 트랜잭션을 관리하기 위한 Bean입니다.
     * @Transactional 애노테이션 대신 코드로 직접 트랜잭션을 제어할 수 있습니다.
     */
    @Bean
    fun transactionalOperator(transactionManager: ReactiveTransactionManager): TransactionalOperator {
        return TransactionalOperator.create(transactionManager)
    }
}
