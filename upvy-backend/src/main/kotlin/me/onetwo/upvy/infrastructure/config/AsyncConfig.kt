package me.onetwo.upvy.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * Spring Async 설정
 *
 * 비동기 이벤트 처리를 위한 ThreadPool 설정입니다.
 *
 * ## Spring Event와 함께 사용
 * - @Async 어노테이션과 함께 사용
 * - @TransactionalEventListener와 조합하여 비동기 이벤트 처리
 *
 * ## ThreadPool 설정
 * - Core Pool Size: 5 (기본 스레드 개수)
 * - Max Pool Size: 10 (최대 스레드 개수)
 * - Queue Capacity: 100 (대기 큐 크기)
 *
 * ## 사용 예시
 * ```kotlin
 * @Async
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * fun handleEvent(event: UserInteractionEvent) {
 *     // 비동기로 실행됨
 * }
 * ```
 */
@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {

    companion object {
        private const val CORE_POOL_SIZE = 5
        private const val MAX_POOL_SIZE = 10
        private const val QUEUE_CAPACITY = 100
        private const val THREAD_NAME_PREFIX = "async-event-"
    }

    /**
     * 비동기 작업을 처리할 ThreadPoolTaskExecutor 설정
     *
     * @return ThreadPoolTaskExecutor 인스턴스
     */
    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = CORE_POOL_SIZE
        executor.maxPoolSize = MAX_POOL_SIZE
        executor.queueCapacity = QUEUE_CAPACITY
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX)
        executor.initialize()
        return executor
    }

    /**
     * 비동기 작업에서 처리되지 않은 예외를 처리
     *
     * 로그만 남기고 애플리케이션이 중단되지 않도록 합니다.
     */
    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncUncaughtExceptionHandler { throwable, method, params ->
            LoggerFactory.getLogger(AsyncConfig::class.java).error(
                "Async method {} threw exception with params {}",
                method.name,
                params.joinToString(", "),
                throwable
            )
        }
    }
}
