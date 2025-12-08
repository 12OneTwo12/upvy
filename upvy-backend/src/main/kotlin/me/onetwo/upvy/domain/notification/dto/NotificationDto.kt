package me.onetwo.upvy.domain.notification.dto

import me.onetwo.upvy.domain.notification.model.Notification
import me.onetwo.upvy.domain.notification.model.NotificationTargetType
import me.onetwo.upvy.domain.notification.model.NotificationType
import java.time.Instant

/**
 * 알림 응답 DTO
 *
 * @property id 알림 ID
 * @property type 알림 유형
 * @property title 알림 제목
 * @property body 알림 본문
 * @property data 추가 데이터 (JSON 문자열)
 * @property isRead 읽음 여부
 * @property actorId 알림 발생 주체 ID
 * @property actorNickname 알림 발생 주체 닉네임
 * @property actorProfileImageUrl 알림 발생 주체 프로필 이미지 URL
 * @property targetType 타겟 유형
 * @property targetId 타겟 ID
 * @property createdAt 생성 시각
 */
data class NotificationResponse(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val data: String?,
    val isRead: Boolean,
    val actorId: String?,
    val actorNickname: String?,
    val actorProfileImageUrl: String?,
    val targetType: NotificationTargetType?,
    val targetId: String?,
    val createdAt: Instant
) {
    companion object {
        fun from(
            notification: Notification,
            actorNickname: String? = null,
            actorProfileImageUrl: String? = null
        ): NotificationResponse {
            return NotificationResponse(
                id = notification.id!!,
                type = notification.type,
                title = notification.title,
                body = notification.body,
                data = notification.data,
                isRead = notification.isRead,
                actorId = notification.actorId?.toString(),
                actorNickname = actorNickname,
                actorProfileImageUrl = actorProfileImageUrl,
                targetType = notification.targetType,
                targetId = notification.targetId?.toString(),
                createdAt = notification.createdAt
            )
        }
    }
}

/**
 * 알림 목록 응답 DTO (커서 기반 페이징)
 *
 * @property notifications 알림 목록
 * @property nextCursor 다음 페이지 커서 (null이면 마지막 페이지)
 * @property hasNext 다음 페이지 존재 여부
 */
data class NotificationListResponse(
    val notifications: List<NotificationResponse>,
    val nextCursor: Long?,
    val hasNext: Boolean
)

/**
 * 읽지 않은 알림 수 응답 DTO
 *
 * @property unreadCount 읽지 않은 알림 수
 */
data class UnreadNotificationCountResponse(
    val unreadCount: Long
)
