package me.onetwo.growsnap.domain.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import me.onetwo.growsnap.domain.notification.model.NotificationTargetType
import me.onetwo.growsnap.domain.notification.model.NotificationType
import me.onetwo.growsnap.domain.notification.model.PushProvider
import me.onetwo.growsnap.infrastructure.notification.push.PushProviderClient
import me.onetwo.growsnap.domain.notification.repository.PushTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 푸시 알림 서비스 구현체
 *
 * 알림 설정 확인, 알림 저장, 푸시 알림 발송을 처리합니다.
 *
 * @property notificationService 알림 서비스 (DB 저장용)
 * @property notificationSettingsService 알림 설정 서비스
 * @property pushTokenRepository 푸시 토큰 Repository
 * @property pushProviders 푸시 제공자 목록
 * @property objectMapper JSON 직렬화용
 */
@Service
class PushNotificationServiceImpl(
    private val notificationService: NotificationService,
    private val notificationSettingsService: NotificationSettingsService,
    private val pushTokenRepository: PushTokenRepository,
    private val pushProviders: List<PushProviderClient>,
    private val objectMapper: ObjectMapper
) : PushNotificationService {

    private val logger = LoggerFactory.getLogger(PushNotificationServiceImpl::class.java)

    private val providerMap: Map<PushProvider, PushProviderClient> by lazy {
        pushProviders.associateBy { it.providerType }
    }

    override fun sendNotification(
        userId: UUID,
        type: NotificationType,
        title: String,
        body: String,
        actorId: UUID?,
        targetType: NotificationTargetType?,
        targetId: UUID?,
        data: String?
    ): Mono<Unit> {
        // 자기 자신에게 알림을 보내지 않음
        if (actorId != null && actorId == userId) {
            logger.debug("Skipping notification to self: userId={}", userId)
            return Mono.just(Unit)
        }

        return checkNotificationEnabled(userId, type)
            .flatMap { enabled ->
                if (!enabled) {
                    logger.debug("Notification disabled for user: userId={}, type={}", userId, type)
                    return@flatMap Mono.just(Unit)
                }

                // 알림 저장
                notificationService.createNotification(
                    userId = userId,
                    type = type,
                    title = title,
                    body = body,
                    actorId = actorId,
                    targetType = targetType,
                    targetId = targetId,
                    data = data
                ).flatMap { notification ->
                    // 푸시 알림 발송
                    sendPushToUser(userId, title, body, data)
                        .doOnSuccess {
                            logger.info(
                                "Notification sent: notificationId={}, userId={}, type={}",
                                notification.id,
                                userId,
                                type
                            )
                        }
                }.thenReturn(Unit)
            }
    }

    override fun sendLikeNotification(
        contentOwnerId: UUID,
        actorId: UUID,
        actorNickname: String,
        contentId: UUID
    ): Mono<Unit> {
        val data = objectMapper.writeValueAsString(
            mapOf(
                "type" to "LIKE",
                "contentId" to contentId.toString()
            )
        )

        return sendNotification(
            userId = contentOwnerId,
            type = NotificationType.LIKE,
            title = "새로운 좋아요",
            body = "${actorNickname}님이 게시물을 좋아합니다.",
            actorId = actorId,
            targetType = NotificationTargetType.CONTENT,
            targetId = contentId,
            data = data
        )
    }

    override fun sendCommentNotification(
        contentOwnerId: UUID,
        actorId: UUID,
        actorNickname: String,
        contentId: UUID,
        commentId: UUID
    ): Mono<Unit> {
        val data = objectMapper.writeValueAsString(
            mapOf(
                "type" to "COMMENT",
                "contentId" to contentId.toString(),
                "commentId" to commentId.toString()
            )
        )

        return sendNotification(
            userId = contentOwnerId,
            type = NotificationType.COMMENT,
            title = "새로운 댓글",
            body = "${actorNickname}님이 댓글을 남겼습니다.",
            actorId = actorId,
            targetType = NotificationTargetType.CONTENT,
            targetId = contentId,
            data = data
        )
    }

    override fun sendReplyNotification(
        commentOwnerId: UUID,
        actorId: UUID,
        actorNickname: String,
        contentId: UUID,
        commentId: UUID
    ): Mono<Unit> {
        val data = objectMapper.writeValueAsString(
            mapOf(
                "type" to "REPLY",
                "contentId" to contentId.toString(),
                "commentId" to commentId.toString()
            )
        )

        return sendNotification(
            userId = commentOwnerId,
            type = NotificationType.REPLY,
            title = "새로운 답글",
            body = "${actorNickname}님이 답글을 남겼습니다.",
            actorId = actorId,
            targetType = NotificationTargetType.COMMENT,
            targetId = commentId,
            data = data
        )
    }

    override fun sendFollowNotification(
        followedUserId: UUID,
        actorId: UUID,
        actorNickname: String
    ): Mono<Unit> {
        val data = objectMapper.writeValueAsString(
            mapOf(
                "type" to "FOLLOW",
                "userId" to actorId.toString()
            )
        )

        return sendNotification(
            userId = followedUserId,
            type = NotificationType.FOLLOW,
            title = "새로운 팔로워",
            body = "${actorNickname}님이 팔로우하기 시작했습니다.",
            actorId = actorId,
            targetType = NotificationTargetType.USER,
            targetId = actorId,
            data = data
        )
    }

    /**
     * 알림 설정 확인
     */
    private fun checkNotificationEnabled(userId: UUID, type: NotificationType): Mono<Boolean> {
        return notificationSettingsService.getSettings(userId)
            .map { settings ->
                if (!settings.allNotificationsEnabled) {
                    return@map false
                }
                when (type) {
                    NotificationType.LIKE -> settings.likeNotificationsEnabled
                    NotificationType.COMMENT, NotificationType.REPLY -> settings.commentNotificationsEnabled
                    NotificationType.FOLLOW -> settings.followNotificationsEnabled
                }
            }
            .defaultIfEmpty(true)
    }

    /**
     * 사용자에게 푸시 알림 발송
     */
    private fun sendPushToUser(
        userId: UUID,
        title: String,
        body: String,
        data: String?
    ): Mono<Unit> {
        return pushTokenRepository.findByUserId(userId)
            .collectList()
            .flatMap { tokens ->
                if (tokens.isEmpty()) {
                    logger.debug("No push tokens found for user: userId={}", userId)
                    return@flatMap Mono.just(Unit)
                }

                // 제공자별로 그룹화하여 발송
                val tokensByProvider = tokens.groupBy { it.provider }
                val sendMonos = tokensByProvider.mapNotNull { (provider, providerTokens) ->
                    val client = providerMap[provider]
                    if (client == null) {
                        logger.warn("No push provider client for: provider={}", provider)
                        return@mapNotNull null
                    }
                    client.sendPush(providerTokens.map { it.token }, title, body, data)
                }

                if (sendMonos.isEmpty()) {
                    return@flatMap Mono.just(Unit)
                }

                Mono.`when`(sendMonos).thenReturn(Unit)
            }
    }
}
