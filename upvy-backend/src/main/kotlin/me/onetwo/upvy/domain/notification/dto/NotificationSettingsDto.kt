package me.onetwo.upvy.domain.notification.dto

import me.onetwo.upvy.domain.notification.model.NotificationSettings
import java.time.Instant

/**
 * 알림 설정 응답 DTO
 *
 * @property allNotificationsEnabled 전체 알림 활성화 여부
 * @property likeNotificationsEnabled 좋아요 알림 활성화 여부
 * @property commentNotificationsEnabled 댓글 알림 활성화 여부
 * @property followNotificationsEnabled 팔로우 알림 활성화 여부
 * @property updatedAt 마지막 수정 시각
 */
data class NotificationSettingsResponse(
    val allNotificationsEnabled: Boolean,
    val likeNotificationsEnabled: Boolean,
    val commentNotificationsEnabled: Boolean,
    val followNotificationsEnabled: Boolean,
    val updatedAt: Instant
) {
    companion object {
        fun from(settings: NotificationSettings): NotificationSettingsResponse {
            return NotificationSettingsResponse(
                allNotificationsEnabled = settings.allNotificationsEnabled,
                likeNotificationsEnabled = settings.likeNotificationsEnabled,
                commentNotificationsEnabled = settings.commentNotificationsEnabled,
                followNotificationsEnabled = settings.followNotificationsEnabled,
                updatedAt = settings.updatedAt
            )
        }
    }
}

/**
 * 알림 설정 수정 요청 DTO
 *
 * 모든 필드는 선택적이며, 제공된 필드만 수정됩니다.
 *
 * @property allNotificationsEnabled 전체 알림 활성화 여부 (null이면 변경하지 않음)
 * @property likeNotificationsEnabled 좋아요 알림 활성화 여부 (null이면 변경하지 않음)
 * @property commentNotificationsEnabled 댓글 알림 활성화 여부 (null이면 변경하지 않음)
 * @property followNotificationsEnabled 팔로우 알림 활성화 여부 (null이면 변경하지 않음)
 */
data class UpdateNotificationSettingsRequest(
    val allNotificationsEnabled: Boolean? = null,
    val likeNotificationsEnabled: Boolean? = null,
    val commentNotificationsEnabled: Boolean? = null,
    val followNotificationsEnabled: Boolean? = null
)
