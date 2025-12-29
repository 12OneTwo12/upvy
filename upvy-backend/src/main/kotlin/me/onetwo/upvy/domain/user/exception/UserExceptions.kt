package me.onetwo.upvy.domain.user.exception

import me.onetwo.upvy.infrastructure.exception.BusinessException
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * 사용자를 찾을 수 없을 때 발생하는 예외
 *
 * @property message 예외 메시지
 */
class UserNotFoundException(
    message: String = "사용자를 찾을 수 없습니다."
) : BusinessException(
    errorCode = "USER_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND,
    message = message
)

/**
 * 사용자 프로필을 찾을 수 없을 때 발생하는 예외
 *
 * @property message 예외 메시지
 */
class UserProfileNotFoundException(
    message: String = "사용자 프로필을 찾을 수 없습니다."
) : BusinessException(
    errorCode = "USER_PROFILE_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND,
    message = message
)

/**
 * 중복된 닉네임으로 등록 시도 시 발생하는 예외
 *
 * @property nickname 중복된 닉네임
 */
class DuplicateNicknameException(
    val nickname: String
) : BusinessException(
    errorCode = "DUPLICATE_NICKNAME",
    httpStatus = HttpStatus.CONFLICT,
    message = "이미 사용 중인 닉네임입니다: $nickname"
)

/**
 * 중복된 이메일로 등록 시도 시 발생하는 예외
 *
 * @property email 중복된 이메일
 */
class DuplicateEmailException(
    val email: String
) : BusinessException(
    errorCode = "DUPLICATE_EMAIL",
    httpStatus = HttpStatus.CONFLICT,
    message = "이미 사용 중인 이메일입니다: $email"
)

/**
 * 이미 팔로우 중인 사용자를 팔로우 시도 시 발생하는 예외
 *
 * @property followingId 팔로우 대상 사용자 ID
 */
class AlreadyFollowingException(
    val followingId: UUID
) : BusinessException(
    errorCode = "ALREADY_FOLLOWING",
    httpStatus = HttpStatus.CONFLICT,
    message = "이미 팔로우 중인 사용자입니다."
)

/**
 * 자기 자신을 팔로우 시도 시 발생하는 예외
 */
class CannotFollowSelfException : BusinessException(
    errorCode = "CANNOT_FOLLOW_SELF",
    httpStatus = HttpStatus.BAD_REQUEST,
    message = "자기 자신을 팔로우할 수 없습니다."
)

/**
 * 팔로우하지 않은 사용자를 언팔로우 시도 시 발생하는 예외
 *
 * @property followingId 언팔로우 대상 사용자 ID
 */
class NotFollowingException(
    val followingId: UUID
) : BusinessException(
    errorCode = "NOT_FOLLOWING",
    httpStatus = HttpStatus.BAD_REQUEST,
    message = "팔로우하지 않은 사용자입니다."
)

/**
 * 사용자 상태 변경 이력을 찾을 수 없을 때 발생하는 예외
 *
 * @property message 예외 메시지
 */
class UserStatusHistoryNotFoundException(
    message: String = "사용자 상태 변경 이력을 찾을 수 없습니다."
) : BusinessException(
    errorCode = "USER_STATUS_HISTORY_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND,
    message = message
)
