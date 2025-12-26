package me.onetwo.upvy.domain.app.dto

import me.onetwo.upvy.domain.app.model.AppVersion

/**
 * 플랫폼별 버전 정보 DTO
 *
 * 관리자 API에서 플랫폼별 버전 설정을 조회하거나 업데이트할 때 사용합니다.
 *
 * @property platform 플랫폼
 * @property minimumVersion 최소 지원 버전
 * @property latestVersion 최신 버전
 * @property storeUrl 앱스토어/플레이스토어 URL
 * @property forceUpdate 강제 업데이트 여부
 */
data class PlatformVersionInfo(
    val platform: String,
    val minimumVersion: String,
    val latestVersion: String,
    val storeUrl: String,
    val forceUpdate: Boolean
) {
    companion object {
        /**
         * AppVersion 엔티티로부터 PlatformVersionInfo 생성
         *
         * @param appVersion 앱 버전 엔티티
         * @return PlatformVersionInfo
         */
        fun from(appVersion: AppVersion): PlatformVersionInfo {
            return PlatformVersionInfo(
                platform = appVersion.platform.name,
                minimumVersion = appVersion.minimumVersion,
                latestVersion = appVersion.latestVersion,
                storeUrl = appVersion.storeUrl,
                forceUpdate = appVersion.forceUpdate
            )
        }
    }
}
