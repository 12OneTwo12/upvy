package me.onetwo.upvy.domain.analytics.event

import jakarta.annotation.PostConstruct
import me.onetwo.upvy.domain.analytics.service.UserContentInteractionService
import me.onetwo.upvy.infrastructure.event.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 사용자 인터랙션 이벤트 Subscriber
 *
 * UserInteractionEvent를 수신하여 user_content_interactions 테이블에 저장
 *
 * ## 처리 패턴
 * - @PostConstruct에서 Flux 구독 시작
 * - subscribeOn(boundedElastic())으로 별도 스레드 실행
 * - onErrorResume()으로 에러 격리 (실패해도 메인 로직 영향 없음)
 *
 * @property userContentInteractionService 사용자별 콘텐츠 인터랙션 서비스
 * @property domainEventFlux 도메인 이벤트 Flux
 */
@Component
class UserInteractionEventSubscriber(
    private val userContentInteractionService: UserContentInteractionService,
    private val domainEventFlux: Flux<DomainEvent>
) {

    /**
     * 이벤트 구독 시작
     *
     * 애플리케이션 시작 시 자동으로 Flux 구독
     */
    @PostConstruct
    fun subscribe() {
        domainEventFlux
            .filter { it is UserInteractionEvent }
            .cast(UserInteractionEvent::class.java)
            .flatMap { event ->
                handleUserInteractionEvent(event)
                    .subscribeOn(Schedulers.boundedElastic())  // 별도 스레드
                    .onErrorResume { error ->
                        logger.error(
                            "Failed to handle UserInteractionEvent: " +
                            "userId={}, contentId={}, type={}",
                            event.userId,
                            event.contentId,
                            event.interactionType,
                            error
                        )
                        Mono.empty()  // 에러 격리: 메인 로직에 영향 없음
                    }
            }
            .subscribe()
    }

    /**
     * 사용자 인터랙션 이벤트 처리
     *
     * user_content_interactions 테이블에 저장 (협업 필터링용)
     *
     * @param event 사용자 인터랙션 이벤트
     * @return Mono<Void>
     */
    private fun handleUserInteractionEvent(event: UserInteractionEvent): Mono<Void> {
        logger.debug(
            "Handling UserInteractionEvent: userId={}, contentId={}, type={}",
            event.userId,
            event.contentId,
            event.interactionType
        )

        return userContentInteractionService.saveUserInteraction(
            event.userId,
            event.contentId,
            event.interactionType
        )
        .doOnSuccess {
            logger.debug("UserInteractionEvent handled successfully")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserInteractionEventSubscriber::class.java)
    }
}
