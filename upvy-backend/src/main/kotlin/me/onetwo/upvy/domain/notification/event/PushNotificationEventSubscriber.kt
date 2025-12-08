package me.onetwo.upvy.domain.notification.event

import jakarta.annotation.PostConstruct
import me.onetwo.upvy.domain.notification.service.PushNotificationService
import me.onetwo.upvy.infrastructure.event.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 푸시 알림 이벤트 Subscriber
 *
 * 좋아요, 댓글, 답글, 팔로우 이벤트를 구독하여 푸시 알림을 비동기로 발송합니다.
 *
 * @property domainEventFlux 도메인 이벤트 Flux
 * @property pushNotificationService 푸시 알림 서비스
 */
@Component
class PushNotificationEventSubscriber(
    private val domainEventFlux: Flux<DomainEvent>,
    private val pushNotificationService: PushNotificationService
) {

    private val logger = LoggerFactory.getLogger(PushNotificationEventSubscriber::class.java)

    /**
     * 이벤트 구독 시작
     */
    @PostConstruct
    fun subscribe() {
        // 좋아요 알림 이벤트 구독
        domainEventFlux
            .filter { it is LikeNotificationEvent }
            .cast(LikeNotificationEvent::class.java)
            .flatMap { event ->
                handleLikeNotificationEvent(event)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume { error ->
                        logger.error(
                            "Failed to send like notification: eventId={}",
                            event.eventId,
                            error
                        )
                        Mono.empty()
                    }
            }
            .subscribe()

        // 댓글 알림 이벤트 구독
        domainEventFlux
            .filter { it is CommentNotificationEvent }
            .cast(CommentNotificationEvent::class.java)
            .flatMap { event ->
                handleCommentNotificationEvent(event)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume { error ->
                        logger.error(
                            "Failed to send comment notification: eventId={}",
                            event.eventId,
                            error
                        )
                        Mono.empty()
                    }
            }
            .subscribe()

        // 답글 알림 이벤트 구독
        domainEventFlux
            .filter { it is ReplyNotificationEvent }
            .cast(ReplyNotificationEvent::class.java)
            .flatMap { event ->
                handleReplyNotificationEvent(event)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume { error ->
                        logger.error(
                            "Failed to send reply notification: eventId={}",
                            event.eventId,
                            error
                        )
                        Mono.empty()
                    }
            }
            .subscribe()

        // 팔로우 알림 이벤트 구독
        domainEventFlux
            .filter { it is FollowNotificationEvent }
            .cast(FollowNotificationEvent::class.java)
            .flatMap { event ->
                handleFollowNotificationEvent(event)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume { error ->
                        logger.error(
                            "Failed to send follow notification: eventId={}",
                            event.eventId,
                            error
                        )
                        Mono.empty()
                    }
            }
            .subscribe()
    }

    /**
     * 좋아요 알림 이벤트 처리
     */
    private fun handleLikeNotificationEvent(event: LikeNotificationEvent): Mono<Unit> {
        logger.debug(
            "Handling LikeNotificationEvent: eventId={}, contentOwnerId={}, actorId={}",
            event.eventId,
            event.contentOwnerId,
            event.actorId
        )

        return pushNotificationService.sendLikeNotification(
            contentOwnerId = event.contentOwnerId,
            actorId = event.actorId,
            actorNickname = event.actorNickname,
            contentId = event.contentId
        ).doOnSuccess {
            logger.info("Like notification sent via event: eventId={}", event.eventId)
        }
    }

    /**
     * 댓글 알림 이벤트 처리
     */
    private fun handleCommentNotificationEvent(event: CommentNotificationEvent): Mono<Unit> {
        logger.debug(
            "Handling CommentNotificationEvent: eventId={}, contentOwnerId={}, actorId={}",
            event.eventId,
            event.contentOwnerId,
            event.actorId
        )

        return pushNotificationService.sendCommentNotification(
            contentOwnerId = event.contentOwnerId,
            actorId = event.actorId,
            actorNickname = event.actorNickname,
            contentId = event.contentId,
            commentId = event.commentId
        ).doOnSuccess {
            logger.info("Comment notification sent via event: eventId={}", event.eventId)
        }
    }

    /**
     * 답글 알림 이벤트 처리
     */
    private fun handleReplyNotificationEvent(event: ReplyNotificationEvent): Mono<Unit> {
        logger.debug(
            "Handling ReplyNotificationEvent: eventId={}, commentOwnerId={}, actorId={}",
            event.eventId,
            event.commentOwnerId,
            event.actorId
        )

        return pushNotificationService.sendReplyNotification(
            commentOwnerId = event.commentOwnerId,
            actorId = event.actorId,
            actorNickname = event.actorNickname,
            contentId = event.contentId,
            commentId = event.commentId
        ).doOnSuccess {
            logger.info("Reply notification sent via event: eventId={}", event.eventId)
        }
    }

    /**
     * 팔로우 알림 이벤트 처리
     */
    private fun handleFollowNotificationEvent(event: FollowNotificationEvent): Mono<Unit> {
        logger.debug(
            "Handling FollowNotificationEvent: eventId={}, followedUserId={}, actorId={}",
            event.eventId,
            event.followedUserId,
            event.actorId
        )

        return pushNotificationService.sendFollowNotification(
            followedUserId = event.followedUserId,
            actorId = event.actorId,
            actorNickname = event.actorNickname
        ).doOnSuccess {
            logger.info("Follow notification sent via event: eventId={}", event.eventId)
        }
    }
}
