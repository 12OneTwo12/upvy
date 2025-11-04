package me.onetwo.growsnap.infrastructure.event

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

/**
 * Reactive Event Bus 설정
 *
 * Reactor Sinks API 기반 Non-blocking Event Bus
 * - multicast: 여러 Subscriber에게 이벤트 브로드캐스트
 * - onBackpressureBuffer(1000): 시스템 과부하 방지
 * - Hot Stream: 구독 시점과 관계없이 실시간 이벤트 수신
 *
 * ## 사용 예시
 * ```kotlin
 * // 발행: eventPublisher.publish(event)
 * // 구독: domainEventFlux.filter { it is MyEvent }.flatMap { ... }.subscribe()
 * ```
 */
@Configuration
class ReactiveEventBusConfig {

    /**
     * 도메인 이벤트 Sink
     *
     * 이벤트를 발행하는 Publisher 역할
     */
    @Bean
    fun domainEventSink(): Sinks.Many<DomainEvent> =
        Sinks.many()
            .multicast()
            .onBackpressureBuffer(1000)

    /**
     * 도메인 이벤트 Flux
     *
     * 이벤트를 구독하는 Subscriber들이 사용하는 Hot Stream
     */
    @Bean
    fun domainEventFlux(sink: Sinks.Many<DomainEvent>): Flux<DomainEvent> =
        sink.asFlux()
            .share()  // Hot stream으로 변환
}
