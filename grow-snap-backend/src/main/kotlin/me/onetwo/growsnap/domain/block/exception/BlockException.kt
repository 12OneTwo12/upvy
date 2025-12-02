package me.onetwo.growsnap.domain.block.exception

import me.onetwo.growsnap.infrastructure.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * 차단 관련 예외
 *
 * 차단 처리 중 발생하는 예외를 정의합니다.
 */
sealed class BlockException(
    errorCode: String,
    httpStatus: HttpStatus,
    message: String
) : BusinessException(errorCode, httpStatus, message) {

    /**
     * 자기 자신을 차단하려는 예외
     *
     * 사용자가 자기 자신을 차단하려고 할 때 발생합니다.
     *
     * @param userId 사용자 ID
     */
    class SelfBlockException(
        userId: String
    ) : BlockException(
        errorCode = "SELF_BLOCK_NOT_ALLOWED",
        httpStatus = HttpStatus.BAD_REQUEST,
        message = "자기 자신은 차단할 수 없습니다. userId=$userId"
    )

    /**
     * 중복 사용자 차단 예외
     *
     * 이미 차단한 사용자를 다시 차단하려고 할 때 발생합니다.
     *
     * @param blockerId 차단한 사용자 ID
     * @param blockedId 차단된 사용자 ID
     */
    class DuplicateUserBlockException(
        blockerId: String,
        blockedId: String
    ) : BlockException(
        errorCode = "DUPLICATE_USER_BLOCK",
        httpStatus = HttpStatus.CONFLICT,
        message = "이미 차단한 사용자입니다. blockerId=$blockerId, blockedId=$blockedId"
    )

    /**
     * 중복 콘텐츠 차단 예외
     *
     * 이미 차단한 콘텐츠를 다시 차단하려고 할 때 발생합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     */
    class DuplicateContentBlockException(
        userId: String,
        contentId: String
    ) : BlockException(
        errorCode = "DUPLICATE_CONTENT_BLOCK",
        httpStatus = HttpStatus.CONFLICT,
        message = "이미 차단한 콘텐츠입니다. userId=$userId, contentId=$contentId"
    )

    /**
     * 사용자 차단을 찾을 수 없는 예외
     *
     * 요청한 사용자 차단이 존재하지 않는 경우 발생합니다.
     *
     * @param blockerId 차단한 사용자 ID
     * @param blockedId 차단된 사용자 ID
     */
    class UserBlockNotFoundException(
        blockerId: String,
        blockedId: String
    ) : BlockException(
        errorCode = "USER_BLOCK_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "사용자 차단을 찾을 수 없습니다. blockerId=$blockerId, blockedId=$blockedId"
    )

    /**
     * 콘텐츠 차단을 찾을 수 없는 예외
     *
     * 요청한 콘텐츠 차단이 존재하지 않는 경우 발생합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     */
    class ContentBlockNotFoundException(
        userId: String,
        contentId: String
    ) : BlockException(
        errorCode = "CONTENT_BLOCK_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "콘텐츠 차단을 찾을 수 없습니다. userId=$userId, contentId=$contentId"
    )
}
