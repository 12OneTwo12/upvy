package me.onetwo.upvy.domain.app.exception

import me.onetwo.upvy.infrastructure.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * 앱 버전 설정을 찾을 수 없는 경우
 *
 * - HTTP Status: 404 Not Found
 * - Error Code: APP_VERSION_NOT_FOUND
 * - 시나리오: 요청한 플랫폼에 대한 버전 설정이 존재하지 않는 경우
 */
class AppVersionNotFoundException(
    platform: String
) : BusinessException(
    errorCode = "APP_VERSION_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND,
    message = "App version configuration not found for platform: $platform"
)
