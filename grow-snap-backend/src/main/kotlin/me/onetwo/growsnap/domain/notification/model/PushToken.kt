package me.onetwo.growsnap.domain.notification.model

import java.time.Instant
import java.util.UUID

/**
 * 푸시 토큰 엔티티
 *
 * 사용자의 디바이스별 푸시 토큰을 관리합니다.
 * 한 사용자가 여러 디바이스에서 로그인할 수 있으므로 user_id + device_id 조합으로 고유성을 유지합니다.
 * 다양한 푸시 제공자(Expo, FCM, APNs)를 지원할 수 있도록 provider 필드를 포함합니다.
 *
 * @property id 푸시 토큰 ID (자동 생성)
 * @property userId 사용자 ID
 * @property token 푸시 토큰 (Expo: ExponentPushToken[xxx], FCM: fcm_token, APNs: device_token)
 * @property deviceId 디바이스 고유 ID
 * @property deviceType 디바이스 플랫폼 유형 (IOS, ANDROID, WEB, UNKNOWN)
 * @property provider 푸시 알림 제공자 (EXPO, FCM, APNS)
 * @property createdAt 생성 시각
 * @property createdBy 생성자 ID
 * @property updatedAt 수정 시각
 * @property updatedBy 수정자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class PushToken(
    val id: Long? = null,
    val userId: UUID,
    val token: String,
    val deviceId: String,
    val deviceType: DeviceType = DeviceType.UNKNOWN,
    val provider: PushProvider = PushProvider.EXPO,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
