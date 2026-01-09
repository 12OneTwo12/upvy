package me.onetwo.upvy.domain.quiz.exception

import me.onetwo.upvy.infrastructure.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * 퀴즈 도메인 예외
 *
 * 퀴즈 관련 비즈니스 로직에서 발생하는 모든 예외의 기본 클래스입니다.
 * BusinessException을 상속받아 GlobalExceptionHandler에서 적절한 HTTP 상태 코드로 처리됩니다.
 */
sealed class QuizException(
    errorCode: String,
    httpStatus: HttpStatus,
    message: String
) : BusinessException(errorCode, httpStatus, message) {

    /**
     * 퀴즈를 찾을 수 없는 경우
     *
     * HTTP 상태 코드: 404 Not Found
     *
     * @param quizId 퀴즈 ID
     */
    class QuizNotFoundException(quizId: String) : QuizException(
        errorCode = "QUIZ_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "Quiz not found: $quizId"
    )

    /**
     * 콘텐츠에 연결된 퀴즈를 찾을 수 없는 경우
     *
     * HTTP 상태 코드: 404 Not Found
     *
     * @param contentId 콘텐츠 ID
     */
    class QuizNotFoundForContentException(contentId: String) : QuizException(
        errorCode = "QUIZ_NOT_FOUND_FOR_CONTENT",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "Quiz not found for content: $contentId"
    )

    /**
     * 퀴즈 생성 실패
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param reason 실패 이유
     */
    class QuizCreationException(reason: String) : QuizException(
        errorCode = "QUIZ_CREATION_FAILED",
        httpStatus = HttpStatus.BAD_REQUEST,
        message = "Quiz creation failed: $reason"
    )

    /**
     * 퀴즈 수정 실패
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param reason 실패 이유
     */
    class QuizUpdateException(reason: String) : QuizException(
        errorCode = "QUIZ_UPDATE_FAILED",
        httpStatus = HttpStatus.BAD_REQUEST,
        message = "Quiz update failed: $reason"
    )

    /**
     * 잘못된 퀴즈 데이터
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param reason 검증 실패 이유
     */
    class InvalidQuizDataException(reason: String) : QuizException(
        errorCode = "INVALID_QUIZ_DATA",
        httpStatus = HttpStatus.BAD_REQUEST,
        message = "Invalid quiz data: $reason"
    )

    /**
     * 퀴즈 시도 실패
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param reason 실패 이유
     */
    class QuizAttemptException(reason: String) : QuizException(
        errorCode = "QUIZ_ATTEMPT_FAILED",
        httpStatus = HttpStatus.BAD_REQUEST,
        message = "Quiz attempt failed: $reason"
    )

    /**
     * 퀴즈 삭제 실패
     *
     * HTTP 상태 코드: 403 Forbidden
     *
     * @param reason 실패 이유
     */
    class QuizDeletionException(reason: String) : QuizException(
        errorCode = "QUIZ_DELETION_FAILED",
        httpStatus = HttpStatus.FORBIDDEN,
        message = "Quiz deletion failed: $reason"
    )

    /**
     * 이미 존재하는 퀴즈
     *
     * HTTP 상태 코드: 409 Conflict
     *
     * @param contentId 콘텐츠 ID
     */
    class QuizAlreadyExistsException(contentId: String) : QuizException(
        errorCode = "QUIZ_ALREADY_EXISTS",
        httpStatus = HttpStatus.CONFLICT,
        message = "Quiz already exists for content: $contentId"
    )

    /**
     * 퀴즈 시도를 찾을 수 없는 경우
     *
     * HTTP 상태 코드: 404 Not Found
     *
     * @param attemptId 퀴즈 시도 ID
     */
    class QuizAttemptNotFoundException(attemptId: String) : QuizException(
        errorCode = "QUIZ_ATTEMPT_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "Quiz attempt not found: $attemptId"
    )

    /**
     * 퀴즈 옵션을 찾을 수 없는 경우
     *
     * HTTP 상태 코드: 404 Not Found
     *
     * @param optionId 퀴즈 옵션 ID
     */
    class QuizOptionNotFoundException(optionId: String) : QuizException(
        errorCode = "QUIZ_OPTION_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "Quiz option not found: $optionId"
    )
}
