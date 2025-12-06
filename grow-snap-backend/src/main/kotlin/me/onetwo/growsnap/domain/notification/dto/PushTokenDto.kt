package me.onetwo.growsnap.domain.notification.dto

import jakarta.validation.constraints.NotBlank
import me.onetwo.growsnap.domain.notification.model.DeviceType
import me.onetwo.growsnap.domain.notification.model.PushProvider
import me.onetwo.growsnap.domain.notification.model.PushToken

/**
 * 푸시 토큰 등록/갱신 요청 DTO
 *
 * @property token 푸시 토큰 (Expo: ExponentPushToken[xxx], FCM: fcm_token 등)
 * @property deviceId 디바이스 고유 ID
 * @property deviceType 디바이스 플랫폼 유형 (IOS, ANDROID, WEB, UNKNOWN)
 * @property provider 푸시 알림 제공자 (EXPO, FCM, APNS)
 */
data class RegisterPushTokenRequest(
    @field:NotBlank(message = "푸시 토큰은 필수입니다")
    val token: String,

    @field:NotBlank(message = "디바이스 ID는 필수입니다")
    val deviceId: String,

    val deviceType: DeviceType = DeviceType.UNKNOWN,

    val provider: PushProvider = PushProvider.EXPO
)

/**
 * 푸시 토큰 응답 DTO
 *
 * @property token 푸시 토큰
 * @property deviceId 디바이스 ID
 * @property deviceType 디바이스 플랫폼 유형
 * @property provider 푸시 알림 제공자
 */
data class PushTokenResponse(
    val token: String,
    val deviceId: String,
    val deviceType: DeviceType,
    val provider: PushProvider
) {
    companion object {
        fun from(pushToken: PushToken): PushTokenResponse {
            return PushTokenResponse(
                token = pushToken.token,
                deviceId = pushToken.deviceId,
                deviceType = pushToken.deviceType,
                provider = pushToken.provider
            )
        }
    }
}
