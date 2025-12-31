package me.onetwo.upvy.domain.content.exception

import me.onetwo.upvy.infrastructure.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * 콘텐츠 도메인 예외
 *
 * 콘텐츠 관련 비즈니스 로직에서 발생하는 모든 예외의 기본 클래스입니다.
 * GlobalExceptionHandler가 처리할 수 있도록 BusinessException을 상속합니다.
 */
sealed class ContentException(
    errorCode: String,
    httpStatus: HttpStatus,
    message: String
) : BusinessException(errorCode, httpStatus, message) {

    /**
     * 지원하지 않는 콘텐츠 타입
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param contentType 콘텐츠 타입
     * @param operation 수행하려는 작업
     */
    class UnsupportedContentTypeException(contentType: String, operation: String) :
        ContentException(
            errorCode = "UNSUPPORTED_CONTENT_TYPE",
            httpStatus = HttpStatus.BAD_REQUEST,
            message = "$contentType content type does not support $operation"
        )

    /**
     * 잘못된 파일 형식
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param reason 실패 이유
     */
    class InvalidFileException(reason: String) :
        ContentException(
            errorCode = "INVALID_FILE",
            httpStatus = HttpStatus.BAD_REQUEST,
            message = "Invalid file: $reason"
        )

    /**
     * 파일 크기 초과
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param contentType 콘텐츠 타입
     * @param maxSize 최대 허용 크기 (MB)
     */
    class FileSizeLimitExceededException(contentType: String, maxSize: Long) :
        ContentException(
            errorCode = "FILE_SIZE_LIMIT_EXCEEDED",
            httpStatus = HttpStatus.BAD_REQUEST,
            message = "File size exceeds maximum allowed size for $contentType: ${maxSize}MB"
        )

    /**
     * 콘텐츠를 찾을 수 없는 경우
     *
     * HTTP 상태 코드: 404 Not Found
     *
     * @param contentId 콘텐츠 ID
     */
    class ContentNotFoundException(contentId: String) :
        ContentException(
            errorCode = "CONTENT_NOT_FOUND",
            httpStatus = HttpStatus.NOT_FOUND,
            message = "Content not found: $contentId"
        )

    /**
     * 콘텐츠 생성 실패
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param reason 실패 이유
     */
    class ContentCreationException(reason: String) :
        ContentException(
            errorCode = "CONTENT_CREATION_FAILED",
            httpStatus = HttpStatus.BAD_REQUEST,
            message = "Content creation failed: $reason"
        )

    /**
     * 콘텐츠 수정 실패
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param reason 실패 이유
     */
    class ContentUpdateException(reason: String) :
        ContentException(
            errorCode = "CONTENT_UPDATE_FAILED",
            httpStatus = HttpStatus.BAD_REQUEST,
            message = "Content update failed: $reason"
        )

    /**
     * 콘텐츠 삭제 권한 없음
     *
     * HTTP 상태 코드: 403 Forbidden
     *
     * @param reason 실패 이유
     */
    class ContentDeletionException(reason: String) :
        ContentException(
            errorCode = "CONTENT_DELETION_FAILED",
            httpStatus = HttpStatus.FORBIDDEN,
            message = "Content deletion failed: $reason"
        )
}
