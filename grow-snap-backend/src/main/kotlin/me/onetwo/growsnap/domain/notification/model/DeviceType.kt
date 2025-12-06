package me.onetwo.growsnap.domain.notification.model

/**
 * 디바이스 타입
 *
 * 푸시 알림을 받는 디바이스의 플랫폼 유형입니다.
 *
 * @property IOS Apple iOS 디바이스
 * @property ANDROID Google Android 디바이스
 * @property WEB 웹 브라우저 (PWA, Web Push 등)
 * @property UNKNOWN 알 수 없는 디바이스 유형
 */
enum class DeviceType {
    IOS,
    ANDROID,
    WEB,
    UNKNOWN
}
