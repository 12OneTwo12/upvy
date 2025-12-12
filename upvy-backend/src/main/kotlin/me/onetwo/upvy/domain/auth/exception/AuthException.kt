package me.onetwo.upvy.domain.auth.exception

import me.onetwo.upvy.infrastructure.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * 이메일이 이미 존재할 때 발생하는 예외
 *
 * @property email 중복된 이메일
 */
class EmailAlreadyExistsException(
    val email: String
) : BusinessException(
    errorCode = "EMAIL_ALREADY_EXISTS",
    httpStatus = HttpStatus.CONFLICT,
    message = "이미 사용 중인 이메일입니다: $email"
)

/**
 * 유효하지 않은 인증 토큰일 때 발생하는 예외
 */
class InvalidVerificationTokenException : BusinessException(
    errorCode = "INVALID_VERIFICATION_TOKEN",
    httpStatus = HttpStatus.BAD_REQUEST,
    message = "유효하지 않은 인증 토큰입니다."
)

/**
 * 인증 토큰이 만료되었을 때 발생하는 예외
 */
class TokenExpiredException : BusinessException(
    errorCode = "TOKEN_EXPIRED",
    httpStatus = HttpStatus.BAD_REQUEST,
    message = "인증 토큰이 만료되었습니다. 새로운 인증 이메일을 요청해주세요."
)

/**
 * 잘못된 인증 정보일 때 발생하는 예외
 */
class InvalidCredentialsException : BusinessException(
    errorCode = "INVALID_CREDENTIALS",
    httpStatus = HttpStatus.UNAUTHORIZED,
    message = "이메일 또는 비밀번호가 올바르지 않습니다."
)

/**
 * 이메일 인증이 완료되지 않았을 때 발생하는 예외
 */
class EmailNotVerifiedException : BusinessException(
    errorCode = "EMAIL_NOT_VERIFIED",
    httpStatus = HttpStatus.FORBIDDEN,
    message = "이메일 인증이 완료되지 않았습니다. 인증 이메일을 확인해주세요."
)
