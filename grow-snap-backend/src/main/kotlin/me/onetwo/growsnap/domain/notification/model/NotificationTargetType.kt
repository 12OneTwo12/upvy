package me.onetwo.growsnap.domain.notification.model

/**
 * 알림 타겟 유형
 *
 * 알림이 연결된 대상 리소스 유형입니다.
 */
enum class NotificationTargetType {
    /** 콘텐츠 */
    CONTENT,

    /** 댓글 */
    COMMENT,

    /** 사용자 */
    USER
}
