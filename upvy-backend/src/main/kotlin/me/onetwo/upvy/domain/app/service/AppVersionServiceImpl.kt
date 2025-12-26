package me.onetwo.upvy.domain.app.service

import me.onetwo.upvy.domain.app.dto.AppVersionCheckResponse
import me.onetwo.upvy.domain.app.exception.AppVersionNotFoundException
import me.onetwo.upvy.domain.app.model.Platform
import me.onetwo.upvy.domain.app.repository.AppVersionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

/**
 * 앱 버전 서비스 구현체
 *
 * 앱 버전 체크 및 강제 업데이트 로직을 처리합니다.
 *
 * @property appVersionRepository 앱 버전 Repository
 */
@Service
@Transactional(readOnly = true)
class AppVersionServiceImpl(
    private val appVersionRepository: AppVersionRepository
) : AppVersionService {

    private val logger = LoggerFactory.getLogger(AppVersionServiceImpl::class.java)

    /**
     * 앱 버전 체크
     *
     * 클라이언트의 현재 버전과 플랫폼 정보를 받아
     * 강제 업데이트 필요 여부와 최신 버전 정보를 반환합니다.
     *
     * @param platform 플랫폼 (IOS, ANDROID)
     * @param currentVersion 현재 앱 버전 (시맨틱 버전 형식: major.minor.patch)
     * @return 버전 체크 응답 (강제 업데이트 필요 여부, 최신 버전 정보 등)
     * @throws AppVersionNotFoundException 플랫폼에 대한 버전 설정이 존재하지 않는 경우
     */
    override fun checkVersion(platform: Platform, currentVersion: String): Mono<AppVersionCheckResponse> {
        logger.debug("Checking app version: platform=$platform, currentVersion=$currentVersion")

        return appVersionRepository.findByPlatform(platform)
            .switchIfEmpty(
                Mono.error(AppVersionNotFoundException(platform.name))
            )
            .map { appVersion ->
                val response = AppVersionCheckResponse.from(appVersion, currentVersion)

                logger.info(
                    "App version check completed: platform={}, currentVersion={}, needsUpdate={}, isLatestVersion={}",
                    platform,
                    currentVersion,
                    response.needsUpdate,
                    response.isLatestVersion
                )

                response
            }
    }
}
