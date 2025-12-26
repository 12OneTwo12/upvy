package me.onetwo.upvy.domain.app.controller

import jakarta.validation.Valid
import me.onetwo.upvy.domain.app.dto.AppVersionCheckRequest
import me.onetwo.upvy.domain.app.dto.AppVersionCheckResponse
import me.onetwo.upvy.domain.app.model.Platform
import me.onetwo.upvy.domain.app.service.AppVersionService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * 앱 버전 Controller
 *
 * 앱 버전 체크 및 강제 업데이트 API를 제공합니다.
 *
 * ## API 엔드포인트
 * - POST /api/v1/app-version/check - 앱 버전 체크
 *
 * @property appVersionService 앱 버전 Service
 */
@RestController
@RequestMapping(ApiPaths.API_V1_APP_VERSION)
class AppVersionController(
    private val appVersionService: AppVersionService
) {

    /**
     * 앱 버전 체크
     *
     * 클라이언트의 현재 버전과 플랫폼 정보를 받아
     * 강제 업데이트 필요 여부와 최신 버전 정보를 반환합니다.
     *
     * ### 요청 예시
     * ```json
     * {
     *   "platform": "IOS",
     *   "currentVersion": "1.2.0"
     * }
     * ```
     *
     * ### 응답 예시 (업데이트 필요)
     * ```json
     * {
     *   "needsUpdate": true,
     *   "isLatestVersion": false,
     *   "latestVersion": "1.4.2",
     *   "minimumVersion": "1.3.0",
     *   "storeUrl": "https://apps.apple.com/app/upvy/id123456789"
     * }
     * ```
     *
     * ### 응답 예시 (업데이트 불필요)
     * ```json
     * {
     *   "needsUpdate": false,
     *   "isLatestVersion": true,
     *   "latestVersion": "1.4.2",
     *   "minimumVersion": "1.0.0",
     *   "storeUrl": null
     * }
     * ```
     *
     * @param request 앱 버전 체크 요청 (platform, currentVersion)
     * @return 앱 버전 체크 응답 (200 OK)
     */
    @PostMapping("/check")
    fun checkVersion(
        @Valid @RequestBody request: AppVersionCheckRequest
    ): Mono<ResponseEntity<AppVersionCheckResponse>> {
        logger.debug("Checking app version: platform={}, currentVersion={}", request.platform, request.currentVersion)

        // Validation ensures these are non-null when reaching this point
        val platform = Platform.valueOf(requireNotNull(request.platform) { "Platform is required" })
        val currentVersion = requireNotNull(request.currentVersion) { "Current version is required" }

        return appVersionService.checkVersion(platform, currentVersion)
            .map { ResponseEntity.ok(it) }
            .doOnSuccess { response ->
                logger.info(
                    "App version check completed: platform={}, currentVersion={}, needsUpdate={}",
                    request.platform,
                    request.currentVersion,
                    response.body?.needsUpdate
                )
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AppVersionController::class.java)
    }
}
