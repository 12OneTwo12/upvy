package me.onetwo.upvy.domain.tag.exception

import me.onetwo.upvy.infrastructure.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * 태그를 찾을 수 없을 때 발생하는 예외
 *
 * @property message 예외 메시지
 */
class TagNotFoundException(
    message: String
) : BusinessException(
    errorCode = "TAG_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND,
    message = message
)
