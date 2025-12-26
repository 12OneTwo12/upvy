package me.onetwo.upvy.domain.app.dto

import me.onetwo.upvy.domain.app.model.AppVersion

/**
 * 앱 버전 체크 응답 DTO
 *
 * 클라이언트에게 강제 업데이트 필요 여부와 버전 정보를 제공합니다.
 *
 * @property needsUpdate 강제 업데이트 필요 여부
 * @property isLatestVersion 최신 버전인지 여부
 * @property latestVersion 최신 버전
 * @property minimumVersion 최소 지원 버전
 * @property storeUrl 앱스토어/플레이스토어 URL (업데이트가 필요한 경우에만 제공)
 */
data class AppVersionCheckResponse(
    val needsUpdate: Boolean,
    val isLatestVersion: Boolean,
    val latestVersion: String,
    val minimumVersion: String,
    val storeUrl: String?
) {
    companion object {
        /**
         * AppVersion 엔티티로부터 AppVersionCheckResponse 생성
         *
         * @param appVersion 앱 버전 엔티티
         * @param currentVersion 현재 사용자의 앱 버전
         * @return AppVersionCheckResponse
         */
        fun from(appVersion: AppVersion, currentVersion: String): AppVersionCheckResponse {
            val needsUpdate = appVersion.requiresUpdate(currentVersion)
            val isLatestVersion = appVersion.isLatestVersion(currentVersion)

            return AppVersionCheckResponse(
                needsUpdate = needsUpdate,
                isLatestVersion = isLatestVersion,
                latestVersion = appVersion.latestVersion,
                minimumVersion = appVersion.minimumVersion,
                storeUrl = if (needsUpdate) appVersion.storeUrl else null
            )
        }
    }
}
