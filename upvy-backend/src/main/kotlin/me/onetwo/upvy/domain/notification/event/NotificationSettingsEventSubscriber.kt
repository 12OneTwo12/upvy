package me.onetwo.upvy.domain.notification.event

import jakarta.annotation.PostConstruct
import me.onetwo.upvy.domain.notification.service.NotificationSettingsService
import me.onetwo.upvy.domain.user.event.UserCreatedEvent
import me.onetwo.upvy.infrastructure.event.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 알림 설정 이벤트 Subscriber
 *
 * 사용자 생성 이벤트를 구독하여 기본 알림 설정을 비동기로 생성합니다.
 *
 * @property domainEventFlux 도메인 이벤트 Flux
 * @property notificationSettingsService 알림 설정 서비스
 */
@Component
class NotificationSettingsEventSubscriber(
    private val domainEventFlux: Flux<DomainEvent>,
    private val notificationSettingsService: NotificationSettingsService
) {

    /**
     * 이벤트 구독 시작
     *
     * UserCreatedEvent를 필터링하여 알림 설정 생성을 처리합니다.
     */
    @PostConstruct
    fun subscribe() {
        domainEventFlux
            .filter { it is UserCreatedEvent }
            .cast(UserCreatedEvent::class.java)
            .flatMap { event ->
                handleUserCreatedEvent(event)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume { error ->
                        logger.error(
                            "Failed to create default notification settings: userId={}, eventId={}",
                            event.userId,
                            event.eventId,
                            error
                        )
                        Mono.empty()
                    }
            }
            .subscribe()
    }

    /**
     * 사용자 생성 이벤트 처리
     *
     * 기본 알림 설정을 생성합니다.
     */
    private fun handleUserCreatedEvent(event: UserCreatedEvent): Mono<Void> {
        logger.debug(
            "Handling UserCreatedEvent: userId={}, eventId={}",
            event.userId,
            event.eventId
        )

        return notificationSettingsService.createDefaultSettings(event.userId)
            .doOnSuccess {
                logger.info(
                    "Default notification settings created via event: userId={}",
                    event.userId
                )
            }
            .then()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NotificationSettingsEventSubscriber::class.java)
    }
}
