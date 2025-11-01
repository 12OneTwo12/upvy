package me.onetwo.growsnap.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement

/**
 * 트랜잭션 관리 설정
 *
 * JOOQ는 JDBC 기반이므로 일반적인 Spring Transaction Management를 사용합니다.
 *
 * ## WebFlux + JOOQ Hybrid 구조
 * - WebFlux: HTTP 레이어 (Non-blocking)
 * - JOOQ: DB 레이어 (Blocking JDBC)
 * - Transaction: JDBC PlatformTransactionManager
 *
 * ## @TransactionalEventListener 지원
 * - AFTER_COMMIT 이벤트가 트랜잭션 커밋 후 발행됨
 * - @Async와 함께 사용하여 비동기 이벤트 처리
 *
 * ## 주의사항
 * - Blocking I/O (JOOQ)를 Reactor 스케줄러에서 실행 시 `Schedulers.boundedElastic()` 사용 필요
 * - Controller에서 반환하는 Mono/Flux는 Non-blocking이지만, Repository 호출은 Blocking
 */
@Configuration
@EnableTransactionManagement
class TransactionConfig
