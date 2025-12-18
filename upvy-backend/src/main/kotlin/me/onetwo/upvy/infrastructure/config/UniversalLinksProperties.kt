package me.onetwo.upvy.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Universal Links (iOS) & App Links (Android) 설정 프로퍼티
 *
 * application.yml의 `upvy.universal-links`, `upvy.redirect-urls` 설정을 바인딩합니다.
 *
 * @property appleTeamId Apple Developer Team ID
 * @property appleBundleId iOS 앱 Bundle ID
 * @property androidSha256Fingerprint Android 앱 SHA-256 인증서 지문
 * @property androidPackageName Android 앱 패키지명
 * @property appStoreUrl App Store 다운로드 URL (iOS fallback)
 * @property playStoreUrl Play Store 다운로드 URL (Android fallback)
 * @property docsHomepageUrl 문서 홈페이지 URL (Desktop fallback)
 */
@ConfigurationProperties(prefix = "upvy")
data class UniversalLinksProperties(
    val universalLinks: UniversalLinks,
    val redirectUrls: RedirectUrls
) {
    data class UniversalLinks(
        val appleTeamId: String = "PLACEHOLDER_TEAM_ID",
        val appleBundleId: String = "com.upvy.app",
        val androidSha256Fingerprint: String = "PLACEHOLDER_FINGERPRINT",
        val androidPackageName: String = "com.upvy.app"
    )

    data class RedirectUrls(
        val appStore: String = "https://apps.apple.com/app/upvy/id6756291696",
        val playStore: String = "https://play.google.com/store/apps/details?id=com.upvy.app",
        val docsHomepage: String = "https://upvy.org"
    )
}
