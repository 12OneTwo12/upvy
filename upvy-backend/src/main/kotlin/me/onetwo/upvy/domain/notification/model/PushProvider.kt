package me.onetwo.upvy.domain.notification.model

/**
 * 푸시 알림 제공자 유형
 *
 * 다양한 푸시 알림 서비스를 지원하기 위한 열거형입니다.
 *
 * @property EXPO Expo Push Notification Service (React Native Expo 앱용)
 * @property FCM Firebase Cloud Messaging (Android 및 iOS용)
 * @property APNS Apple Push Notification Service (iOS 네이티브용)
 */
enum class PushProvider {
    /**
     * Expo Push Notification Service
     *
     * React Native Expo 앱에서 사용하는 푸시 서비스입니다.
     * 토큰 형식: ExponentPushToken[xxx]
     */
    EXPO,

    /**
     * Firebase Cloud Messaging
     *
     * Google Firebase에서 제공하는 푸시 서비스입니다.
     * Android와 iOS 모두 지원합니다.
     */
    FCM,

    /**
     * Apple Push Notification Service
     *
     * iOS 네이티브 앱에서 사용하는 푸시 서비스입니다.
     */
    APNS
}
