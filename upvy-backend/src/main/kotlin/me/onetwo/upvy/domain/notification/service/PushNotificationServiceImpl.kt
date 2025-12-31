package me.onetwo.upvy.domain.notification.service

import com.fasterxml.jackson.databind.ObjectMapper
import me.onetwo.upvy.domain.notification.model.DeliveryStatus
import me.onetwo.upvy.domain.notification.model.NotificationTargetType
import me.onetwo.upvy.domain.notification.model.NotificationType
import me.onetwo.upvy.domain.notification.model.PushLogStatus
import me.onetwo.upvy.domain.notification.model.PushNotificationLog
import me.onetwo.upvy.domain.notification.model.PushProvider
import me.onetwo.upvy.domain.notification.model.PushToken
import me.onetwo.upvy.domain.notification.repository.NotificationRepository
import me.onetwo.upvy.domain.notification.repository.PushNotificationLogRepository
import me.onetwo.upvy.domain.notification.repository.PushTokenRepository
import me.onetwo.upvy.infrastructure.notification.push.PushProviderClient
import me.onetwo.upvy.infrastructure.notification.push.PushResult
import me.onetwo.upvy.infrastructure.notification.push.TokenResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 푸시 알림 서비스 구현체
 *
 * 알림 설정 확인, 알림 저장, 푸시 알림 발송 및 로깅을 처리합니다.
 *
 * @property notificationService 알림 서비스 (DB 저장용)
 * @property notificationSettingsService 알림 설정 서비스
 * @property notificationRepository 알림 Repository (상태 업데이트용)
 * @property pushTokenRepository 푸시 토큰 Repository
 * @property pushLogRepository 푸시 발송 로그 Repository
 * @property pushProviders 푸시 제공자 목록
 * @property objectMapper JSON 직렬화용
 */
@Service
class PushNotificationServiceImpl(
    private val notificationService: NotificationService,
    private val notificationSettingsService: NotificationSettingsService,
    private val notificationRepository: NotificationRepository,
    private val pushTokenRepository: PushTokenRepository,
    private val pushLogRepository: PushNotificationLogRepository,
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
                if (enabled) {
                    createAndSendNotification(userId, type, title, body, actorId, targetType, targetId, data)
                } else {
                    createSkippedNotification(userId, type, title, body, actorId, targetType, targetId, data)
                }
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
     * 알림 생성 및 푸시 발송
     */
    private fun createAndSendNotification(
        userId: UUID,
        type: NotificationType,
        title: String,
        body: String,
        actorId: UUID?,
        targetType: NotificationTargetType?,
        targetId: UUID?,
        data: String?
    ): Mono<Unit> {
        return notificationService.createNotification(
            userId = userId,
            type = type,
            title = title,
            body = body,
            actorId = actorId,
            targetType = targetType,
            targetId = targetId,
            data = data
        ).flatMap { notification ->
            sendPushToUserWithLogging(notification.id!!, userId, title, body, data)
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

    /**
     * 알림 생성 후 SKIPPED 상태로 저장 (알림 설정 비활성화)
     */
    private fun createSkippedNotification(
        userId: UUID,
        type: NotificationType,
        title: String,
        body: String,
        actorId: UUID?,
        targetType: NotificationTargetType?,
        targetId: UUID?,
        data: String?
    ): Mono<Unit> {
        logger.debug("Notification disabled for user: userId={}, type={}", userId, type)
        return notificationService.createNotification(
            userId = userId,
            type = type,
            title = title,
            body = body,
            actorId = actorId,
            targetType = targetType,
            targetId = targetId,
            data = data
        ).flatMap { notification ->
            notificationRepository.updateDeliveryStatus(notification.id!!, DeliveryStatus.SKIPPED)
        }.thenReturn(Unit)
    }

    /**
     * 사용자에게 푸시 알림 발송 및 로깅
     */
    private fun sendPushToUserWithLogging(
        notificationId: Long,
        userId: UUID,
        title: String,
        body: String,
        data: String?
    ): Mono<Unit> {
        return pushTokenRepository.findByUserId(userId)
            .collectList()
            .flatMap { tokens ->
                if (tokens.isEmpty()) {
                    handleNoTokens(notificationId, userId)
                } else {
                    sendToAllProvidersAndUpdateStatus(notificationId, tokens, title, body, data)
                }
            }
    }

    /**
     * 토큰이 없는 경우 처리 (SKIPPED 상태로 업데이트)
     */
    private fun handleNoTokens(notificationId: Long, userId: UUID): Mono<Unit> {
        logger.debug("No push tokens found for user: userId={}", userId)
        return notificationRepository.updateDeliveryStatus(notificationId, DeliveryStatus.SKIPPED)
            .thenReturn(Unit)
    }

    /**
     * 제공자별로 그룹화하여 푸시 발송 및 결과에 따라 상태 업데이트
     */
    private fun sendToAllProvidersAndUpdateStatus(
        notificationId: Long,
        tokens: List<PushToken>,
        title: String,
        body: String,
        data: String?
    ): Mono<Unit> {
        val tokensByProvider = tokens.groupBy { it.provider }
        return Flux.fromIterable(tokensByProvider.entries)
            .flatMap { (provider, providerTokens) ->
                sendToProviderWithLogging(notificationId, provider, providerTokens, title, body, data)
            }
            .collectList()
            .flatMap { results ->
                val hasSuccess = results.any { it }
                val status = if (hasSuccess) DeliveryStatus.SENT else DeliveryStatus.FAILED
                notificationRepository.updateDeliveryStatus(notificationId, status)
            }
            .thenReturn(Unit)
    }

    /**
     * 특정 프로바이더로 푸시 발송 및 로깅
     */
    private fun sendToProviderWithLogging(
        notificationId: Long,
        provider: PushProvider,
        tokens: List<PushToken>,
        title: String,
        body: String,
        data: String?
    ): Mono<Boolean> {
        val client = providerMap[provider]
        if (client == null) {
            logger.warn("No push provider client for: provider={}", provider)
            return Mono.just(false)
        }

        val sentAt = Instant.now()

        return client.sendPushWithResult(tokens.map { it.token }, title, body, data)
            .flatMap { result -> saveSuccessLogsAndCleanupTokens(notificationId, provider, tokens, result, sentAt) }
            .onErrorResume { error -> saveFailureLogsAndReturnFalse(notificationId, provider, tokens, error, sentAt) }
    }

    /**
     * 성공 로그 저장 및 좀비 토큰 삭제
     */
    private fun saveSuccessLogsAndCleanupTokens(
        notificationId: Long,
        provider: PushProvider,
        tokens: List<PushToken>,
        result: PushResult,
        sentAt: Instant
    ): Mono<Boolean> {
        return Flux.fromIterable(tokens.withIndex())
            .flatMap { (index, token) ->
                val tokenResult = result.tokenResults.getOrNull(index)
                saveLogAndCleanupIfNeeded(notificationId, provider, token, tokenResult, sentAt)
            }
            .collectList()
            .map { result.hasSuccess }
    }

    /**
     * 개별 토큰 로그 저장 및 유효하지 않은 토큰 삭제
     */
    private fun saveLogAndCleanupIfNeeded(
        notificationId: Long,
        provider: PushProvider,
        token: PushToken,
        tokenResult: TokenResult?,
        sentAt: Instant
    ): Mono<Void> {
        val log = PushNotificationLog(
            notificationId = notificationId,
            pushTokenId = token.id,
            provider = provider,
            status = if (tokenResult?.success == true) PushLogStatus.SENT else PushLogStatus.FAILED,
            providerMessageId = tokenResult?.messageId,
            errorCode = tokenResult?.errorCode,
            errorMessage = tokenResult?.errorMessage,
            attemptCount = 1,
            sentAt = sentAt
        )

        return if (isInvalidTokenError(tokenResult?.errorCode)) {
            logger.info("Deleting invalid push token: token={}, errorCode={}", token.token, tokenResult?.errorCode)
            pushLogRepository.save(log)
                .then(pushTokenRepository.deleteByToken(token.token, token.userId))
        } else {
            pushLogRepository.save(log).then()
        }
    }

    /**
     * 실패 로그 저장 및 false 반환
     */
    private fun saveFailureLogsAndReturnFalse(
        notificationId: Long,
        provider: PushProvider,
        tokens: List<PushToken>,
        error: Throwable,
        sentAt: Instant
    ): Mono<Boolean> {
        logger.error("Failed to send push notification: provider={}, error={}", provider, error.message)
        return Flux.fromIterable(tokens)
            .flatMap { token ->
                val log = PushNotificationLog(
                    notificationId = notificationId,
                    pushTokenId = token.id,
                    provider = provider,
                    status = PushLogStatus.FAILED,
                    errorMessage = error.message?.take(500),
                    attemptCount = 1,
                    sentAt = sentAt
                )
                pushLogRepository.save(log)
            }
            .collectList()
            .map { false }
    }

    /**
     * 유효하지 않은 토큰 에러인지 확인
     *
     * Expo Push API 에러 코드:
     * - DeviceNotRegistered: 디바이스가 더 이상 푸시 알림을 받지 않음 (앱 삭제, 토큰 만료 등)
     * - InvalidCredentials: 잘못된 푸시 자격 증명
     *
     * @see https://docs.expo.dev/push-notifications/sending-notifications/#individual-errors
     */
    private fun isInvalidTokenError(errorCode: String?): Boolean {
        return errorCode in INVALID_TOKEN_ERROR_CODES
    }

    companion object {
        /**
         * 유효하지 않은 토큰으로 판단되는 Expo Push API 에러 코드
         */
        private val INVALID_TOKEN_ERROR_CODES = setOf("DeviceNotRegistered", "InvalidCredentials")
    }
}
