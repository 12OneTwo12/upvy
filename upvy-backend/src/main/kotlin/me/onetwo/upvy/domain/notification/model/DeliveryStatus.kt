package me.onetwo.upvy.domain.notification.model

/**
 * 알림 발송 상태
 *
 * 알림의 푸시 발송 진행 상태를 나타냅니다.
 */
enum class DeliveryStatus {
    /**
     * 대기 중 - 아직 푸시 발송 시도 전
     */
    PENDING,

    /**
     * 발송됨 - 푸시 서비스에 성공적으로 전송됨
     */
    SENT,

    /**
     * 전달됨 - 디바이스에 도착 확인됨 (가능한 경우)
     */
    DELIVERED,

    /**
     * 실패 - 푸시 발송 실패
     */
    FAILED,

    /**
     * 건너뜀 - 알림 설정 비활성화 등으로 발송 안함
     */
    SKIPPED
}
