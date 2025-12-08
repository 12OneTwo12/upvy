package me.onetwo.upvy.domain.notification.model

import java.time.Instant

/**
 * 푸시 알림 발송 로그 엔티티
 *
 * 모든 푸시 발송 시도를 기록하는 Append-Only 로그 테이블입니다.
 * 발송 성공/실패 여부, 에러 메시지, 재시도 횟수 등을 저장합니다.
 *
 * @property id 로그 ID (자동 생성)
 * @property notificationId 연관 알림 ID
 * @property pushTokenId 사용된 푸시 토큰 ID
 * @property provider 푸시 제공자 (EXPO, FCM, APNS)
 * @property status 발송 상태 (SENT, DELIVERED, FAILED, EXPIRED)
 * @property providerMessageId 프로바이더 응답 ID (Expo ticket ID 등)
 * @property errorCode 에러 코드
 * @property errorMessage 에러 메시지
 * @property attemptCount 시도 횟수
 * @property sentAt 발송 시각
 * @property deliveredAt 도착 확인 시각
 * @property createdAt 로그 생성 시각
 */
data class PushNotificationLog(
    val id: Long? = null,
    val notificationId: Long,
    val pushTokenId: Long? = null,
    val provider: PushProvider,
    val status: PushLogStatus,
    val providerMessageId: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val attemptCount: Int = 1,
    val sentAt: Instant,
    val deliveredAt: Instant? = null,
    val createdAt: Instant = Instant.now()
)
