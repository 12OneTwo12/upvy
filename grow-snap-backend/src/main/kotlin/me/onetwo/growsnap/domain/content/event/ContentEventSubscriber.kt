package me.onetwo.growsnap.domain.content.event

import jakarta.annotation.PostConstruct
import me.onetwo.growsnap.domain.analytics.service.ContentInteractionService
import me.onetwo.growsnap.infrastructure.event.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 콘텐츠 이벤트 Subscriber - ContentCreatedEvent 처리
 *
 * @property contentInteractionService 콘텐츠 인터랙션 서비스
 * @property domainEventFlux 도메인 이벤트 Flux
 */
@Component
class ContentEventSubscriber(
    private val contentInteractionService: ContentInteractionService,
    private val domainEventFlux: Flux<DomainEvent>
) {

    @PostConstruct
    fun subscribe() {
        domainEventFlux
            .filter { it is ContentCreatedEvent }
            .cast(ContentCreatedEvent::class.java)
            .flatMap { event ->
                handleContentCreated(event)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume { error ->
                        logger.error(
                            "Failed to handle ContentCreatedEvent: contentId={}",
                            event.contentId,
                            error
                        )
                        Mono.empty()
                    }
            }
            .subscribe()
    }

    private fun handleContentCreated(event: ContentCreatedEvent): Mono<Void> {
        logger.debug("Handling ContentCreatedEvent: contentId={}", event.contentId)

        return contentInteractionService.createContentInteraction(event.contentId, event.creatorId)
            .doOnSuccess {
                logger.debug("ContentCreatedEvent handled successfully: contentId={}", event.contentId)
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ContentEventSubscriber::class.java)
    }
}
