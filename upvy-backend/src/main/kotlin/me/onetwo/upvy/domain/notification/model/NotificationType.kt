package me.onetwo.upvy.domain.notification.model

/**
 * 알림 유형
 *
 * 푸시 알림 및 알림 센터에서 사용되는 알림 유형입니다.
 */
enum class NotificationType {
    /** 좋아요 알림 */
    LIKE,

    /** 댓글 알림 */
    COMMENT,

    /** 답글 알림 */
    REPLY,

    /** 팔로우 알림 */
    FOLLOW
}
