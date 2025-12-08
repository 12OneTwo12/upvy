package me.onetwo.upvy.infrastructure.event

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Sinks

/**
 * Reactive Event Publisher
 *
 * Reactor Sinks API를 사용한 이벤트 발행
 * - tryEmitNext()로 안전하게 발행 (실패 시 로그 기록)
 * - FAIL_ZERO_SUBSCRIBER는 정상 상황으로 처리
 */
@Component
class ReactiveEventPublisher(
    private val domainEventSink: Sinks.Many<DomainEvent>
) {

    /**
     * 이벤트 발행
     *
     * @param event 도메인 이벤트
     */
    fun publish(event: DomainEvent) {
        val result = domainEventSink.tryEmitNext(event)

        if (result.isFailure && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            logger.error(
                "Failed to publish event: type={}, eventId={}, reason={}",
                event::class.simpleName,
                event.eventId,
                result
            )
        } else {
            logger.debug(
                "Event published: type={}, eventId={}",
                event::class.simpleName,
                event.eventId
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReactiveEventPublisher::class.java)
    }
}
