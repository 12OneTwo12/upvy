package me.onetwo.upvy.domain.user.event

import jakarta.annotation.PostConstruct
import me.onetwo.upvy.infrastructure.event.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 팔로우 이벤트 Subscriber - 알림 생성 처리
 *
 * @property domainEventFlux 도메인 이벤트 Flux
 */
@Component
class FollowEventSubscriber(
    private val domainEventFlux: Flux<DomainEvent>
) {

    @PostConstruct
    fun subscribe() {
        domainEventFlux
            .filter { it is FollowEvent }
            .cast(FollowEvent::class.java)
            .flatMap { event ->
                handleFollowEvent(event)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume { error ->
                        logger.error(
                            "Failed to handle FollowEvent: follower={}, following={}",
                            event.followerId,
                            event.followingId,
                            error
                        )
                        Mono.empty()
                    }
            }
            .subscribe()
    }

    private fun handleFollowEvent(event: FollowEvent): Mono<Void> {
        logger.debug(
            "Handling FollowEvent: follower={}, following={}",
            event.followerId,
            event.followingId
        )

        // TODO: 추후 알림 시스템 구현 시 notifications 테이블에 저장
        logger.info(
            "Follow notification created: {} followed {}",
            event.followerId,
            event.followingId
        )

        return Mono.empty()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FollowEventSubscriber::class.java)
    }
}
