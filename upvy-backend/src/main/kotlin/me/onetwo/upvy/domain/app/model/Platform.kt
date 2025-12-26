package me.onetwo.upvy.domain.app.model

/**
 * 앱 플랫폼
 *
 * @property displayName 표시 이름
 * @property description 설명
 */
enum class Platform(val displayName: String, val description: String) {
    IOS("iOS", "Apple iOS 플랫폼 (App Store)"),
    ANDROID("Android", "Google Android 플랫폼 (Play Store)")
}
