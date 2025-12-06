package me.onetwo.growsnap.domain.notification.exception

import me.onetwo.growsnap.infrastructure.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * 알림을 찾을 수 없는 경우
 *
 * - HTTP Status: 404 Not Found
 * - Error Code: NOTIFICATION_NOT_FOUND
 * - 시나리오: 존재하지 않거나 권한이 없는 알림에 접근하는 경우
 */
class NotificationNotFoundException(
    message: String
) : BusinessException(
    errorCode = "NOTIFICATION_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND,
    message = message
)
