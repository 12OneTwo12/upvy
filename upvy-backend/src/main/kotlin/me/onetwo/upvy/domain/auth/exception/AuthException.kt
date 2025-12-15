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

/**
 * 너무 많은 요청을 시도했을 때 발생하는 예외
 *
 * 인증 코드 재전송 시 1분 이내 재시도를 방지합니다.
 */
class TooManyRequestsException : BusinessException(
    errorCode = "TOO_MANY_REQUESTS",
    httpStatus = HttpStatus.TOO_MANY_REQUESTS,
    message = "잠시 후 다시 시도해주세요. (1분 후 재전송 가능)"
)

/**
 * OAuth 전용 사용자가 이메일 인증 기능을 사용하려 할 때 발생하는 예외
 *
 * OAuth로만 가입한 사용자는 EMAIL 인증 수단이 없으므로,
 * 비밀번호 변경/재설정 등의 이메일 인증 관련 기능을 사용할 수 없습니다.
 */
class OAuthOnlyUserException : BusinessException(
    errorCode = "OAUTH_ONLY_USER",
    httpStatus = HttpStatus.FORBIDDEN,
    message = "OAuth로만 가입한 사용자는 이 기능을 사용할 수 없습니다. 이메일 인증을 추가해주세요."
)

/**
 * Apple Identity Token이 유효하지 않을 때 발생하는 예외
 *
 * Apple 네이티브 인증 시 전달된 identityToken(JWT)이 다음과 같은 경우 발생합니다:
 * - JWT 서명 검증 실패
 * - JWT 만료
 * - 필수 claim(email, sub 등)이 누락된 경우
 * - Apple 공개키로 검증할 수 없는 토큰
 */
class InvalidAppleTokenException(
    message: String = "Apple Identity Token이 유효하지 않습니다."
) : BusinessException(
    errorCode = "INVALID_APPLE_TOKEN",
    httpStatus = HttpStatus.UNAUTHORIZED,
    message = message
)
