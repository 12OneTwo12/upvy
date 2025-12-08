package me.onetwo.upvy.domain.notification.model

import java.time.Instant
import java.util.UUID

/**
 * 알림 설정 엔티티
 *
 * 사용자별 알림 활성화/비활성화 설정을 관리합니다.
 *
 * @property id 알림 설정 ID (자동 생성)
 * @property userId 사용자 ID
 * @property allNotificationsEnabled 전체 알림 활성화 여부 (false면 개별 설정과 관계없이 모든 알림 비활성화)
 * @property likeNotificationsEnabled 좋아요 알림 활성화 여부
 * @property commentNotificationsEnabled 댓글 알림 활성화 여부
 * @property followNotificationsEnabled 팔로우 알림 활성화 여부
 * @property createdAt 생성 시각
 * @property createdBy 생성자 ID
 * @property updatedAt 수정 시각
 * @property updatedBy 수정자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class NotificationSettings(
    val id: Long? = null,
    val userId: UUID,
    val allNotificationsEnabled: Boolean = true,
    val likeNotificationsEnabled: Boolean = true,
    val commentNotificationsEnabled: Boolean = true,
    val followNotificationsEnabled: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
) {
    companion object {
        /**
         * 신규 사용자를 위한 기본 알림 설정 생성
         *
         * 모든 알림이 활성화된 상태로 생성됩니다.
         *
         * @param userId 사용자 ID
         * @return 기본 알림 설정
         */
        fun createDefault(userId: UUID): NotificationSettings {
            return NotificationSettings(
                userId = userId,
                allNotificationsEnabled = true,
                likeNotificationsEnabled = true,
                commentNotificationsEnabled = true,
                followNotificationsEnabled = true
            )
        }
    }
}
