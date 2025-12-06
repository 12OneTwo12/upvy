package me.onetwo.growsnap.domain.notification.service

import me.onetwo.growsnap.domain.notification.dto.NotificationListResponse
import me.onetwo.growsnap.domain.notification.dto.NotificationResponse
import me.onetwo.growsnap.domain.notification.dto.UnreadNotificationCountResponse
import me.onetwo.growsnap.domain.notification.exception.NotificationNotFoundException
import me.onetwo.growsnap.domain.notification.model.Notification
import me.onetwo.growsnap.domain.notification.model.NotificationTargetType
import me.onetwo.growsnap.domain.notification.model.NotificationType
import me.onetwo.growsnap.domain.notification.repository.NotificationRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 알림 서비스 구현체
 *
 * 알림 생성, 조회, 읽음 처리, 삭제 관련 비즈니스 로직을 처리합니다.
 *
 * @property notificationRepository 알림 Repository
 * @property userProfileRepository 사용자 프로필 Repository
 */
@Service
@Transactional(readOnly = true)
class NotificationServiceImpl(
    private val notificationRepository: NotificationRepository,
    private val userProfileRepository: UserProfileRepository
) : NotificationService {

    private val logger = LoggerFactory.getLogger(NotificationServiceImpl::class.java)

    /**
     * 알림 생성
     */
    @Transactional
    override fun createNotification(
        userId: UUID,
        type: NotificationType,
        title: String,
        body: String,
        actorId: UUID?,
        targetType: NotificationTargetType?,
        targetId: UUID?,
        data: String?
    ): Mono<Notification> {
        logger.debug(
            "Creating notification: userId={}, type={}, actorId={}, targetType={}, targetId={}",
            userId,
            type,
            actorId,
            targetType,
            targetId
        )

        val notification = Notification(
            userId = userId,
            type = type,
            title = title,
            body = body,
            data = data,
            actorId = actorId,
            targetType = targetType,
            targetId = targetId
        )

        return notificationRepository.save(notification)
            .doOnSuccess {
                logger.info(
                    "Notification created: id={}, userId={}, type={}",
                    it.id,
                    userId,
                    type
                )
            }
    }

    /**
     * 알림 목록 조회 (커서 기반 페이징)
     */
    override fun getNotifications(userId: UUID, cursor: Long?, limit: Int): Mono<NotificationListResponse> {
        val fetchLimit = limit + 1

        return notificationRepository.findByUserId(userId, cursor, fetchLimit)
            .collectList()
            .flatMap { notifications ->
                val hasNext = notifications.size > limit
                val resultNotifications = if (hasNext) notifications.dropLast(1) else notifications
                val nextCursor = if (hasNext) resultNotifications.lastOrNull()?.id else null

                val actorIds = resultNotifications.mapNotNull { it.actorId }.toSet()

                if (actorIds.isEmpty()) {
                    Mono.just(
                        NotificationListResponse(
                            notifications = resultNotifications.map { NotificationResponse.from(it) },
                            nextCursor = nextCursor,
                            hasNext = hasNext
                        )
                    )
                } else {
                    userProfileRepository.findUserInfosByUserIds(actorIds)
                        .map { userInfoMap ->
                            NotificationListResponse(
                                notifications = resultNotifications.map { notification ->
                                    val userInfo = notification.actorId?.let { userInfoMap[it] }
                                    NotificationResponse.from(
                                        notification = notification,
                                        actorNickname = userInfo?.nickname,
                                        actorProfileImageUrl = userInfo?.profileImageUrl
                                    )
                                },
                                nextCursor = nextCursor,
                                hasNext = hasNext
                            )
                        }
                }
            }
    }

    /**
     * 읽지 않은 알림 수 조회
     */
    override fun getUnreadCount(userId: UUID): Mono<UnreadNotificationCountResponse> {
        return notificationRepository.countUnreadByUserId(userId)
            .map { count -> UnreadNotificationCountResponse(unreadCount = count) }
    }

    /**
     * 개별 알림 읽음 처리
     */
    @Transactional
    override fun markAsRead(notificationId: Long, userId: UUID): Mono<NotificationResponse> {
        logger.info("Marking notification as read: notificationId={}, userId={}", notificationId, userId)

        return notificationRepository.markAsRead(notificationId, userId)
            .map { notification -> NotificationResponse.from(notification) }
            .switchIfEmpty(Mono.error(NotificationNotFoundException("알림을 찾을 수 없습니다: $notificationId")))
            .doOnSuccess {
                logger.info("Notification marked as read: notificationId={}", notificationId)
            }
    }

    /**
     * 모든 알림 읽음 처리
     */
    @Transactional
    override fun markAllAsRead(userId: UUID): Mono<Void> {
        logger.info("Marking all notifications as read: userId={}", userId)

        return notificationRepository.markAllAsRead(userId)
            .doOnSuccess {
                logger.info("All notifications marked as read: userId={}", userId)
            }
    }

    /**
     * 개별 알림 삭제
     */
    @Transactional
    override fun deleteNotification(notificationId: Long, userId: UUID): Mono<Void> {
        logger.info("Deleting notification: notificationId={}, userId={}", notificationId, userId)

        return notificationRepository.findById(notificationId)
            .filter { notification -> notification.userId == userId }
            .switchIfEmpty(Mono.error(NotificationNotFoundException("알림을 찾을 수 없습니다: $notificationId")))
            .flatMap { notificationRepository.deleteById(notificationId, userId) }
            .doOnSuccess {
                logger.info("Notification deleted: notificationId={}", notificationId)
            }
    }
}
