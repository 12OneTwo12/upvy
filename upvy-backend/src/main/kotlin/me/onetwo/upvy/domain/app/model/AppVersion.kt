package me.onetwo.upvy.domain.app.model

import java.time.Instant

/**
 * 앱 버전 관리 엔티티
 *
 * 플랫폼별 최소 지원 버전과 최신 버전을 관리하여 강제 업데이트 기능을 제공합니다.
 * 사용자의 앱 버전이 최소 버전보다 낮은 경우 강제 업데이트를 요구합니다.
 *
 * @property id 앱 버전 설정 고유 식별자
 * @property platform 플랫폼 (IOS, ANDROID)
 * @property minimumVersion 최소 지원 버전 (이 버전 미만은 강제 업데이트 필요)
 * @property latestVersion 최신 버전
 * @property storeUrl 앱스토어/플레이스토어 URL
 * @property forceUpdate 강제 업데이트 여부 (기본값: true)
 * @property createdAt 생성 시각 (UTC Instant)
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각 (UTC Instant)
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (UTC Instant, Soft Delete)
 */
data class AppVersion(
    val id: Long? = null,
    val platform: Platform,
    val minimumVersion: String,
    val latestVersion: String,
    val storeUrl: String,
    val forceUpdate: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
) {
    /**
     * 주어진 버전이 최소 버전보다 낮은지 확인
     *
     * @param currentVersion 현재 사용자의 앱 버전
     * @return 강제 업데이트가 필요한 경우 true
     */
    fun requiresUpdate(currentVersion: String): Boolean {
        return forceUpdate && compareVersions(currentVersion, minimumVersion) < 0
    }

    /**
     * 주어진 버전이 최신 버전인지 확인
     *
     * @param currentVersion 현재 사용자의 앱 버전
     * @return 최신 버전인 경우 true
     */
    fun isLatestVersion(currentVersion: String): Boolean {
        return compareVersions(currentVersion, latestVersion) >= 0
    }

    /**
     * 시맨틱 버전 비교
     *
     * 버전 형식: major.minor.patch (예: 1.4.2)
     *
     * @param version1 비교할 첫 번째 버전
     * @param version2 비교할 두 번째 버전
     * @return version1 > version2 이면 양수, version1 == version2 이면 0, version1 < version2 이면 음수
     */
    private fun compareVersions(version1: String, version2: String): Int {
        val v1Parts = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = version2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(v1Parts.size, v2Parts.size)

        for (i in 0 until maxLength) {
            val v1Part = v1Parts.getOrNull(i) ?: 0
            val v2Part = v2Parts.getOrNull(i) ?: 0

            if (v1Part != v2Part) {
                return v1Part.compareTo(v2Part)
            }
        }

        return 0
    }
}
