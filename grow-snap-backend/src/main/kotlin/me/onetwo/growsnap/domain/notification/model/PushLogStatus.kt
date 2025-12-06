package me.onetwo.growsnap.domain.notification.model

/**
 * 푸시 발송 로그 상태
 *
 * 개별 푸시 발송 시도의 결과를 나타냅니다.
 */
enum class PushLogStatus {
    /**
     * 발송됨 - 푸시 서비스에 성공적으로 전송됨
     */
    SENT,

    /**
     * 전달됨 - 디바이스에 도착 확인됨
     */
    DELIVERED,

    /**
     * 실패 - 발송 실패
     */
    FAILED,

    /**
     * 만료됨 - 토큰 만료로 발송 불가
     */
    EXPIRED
}
