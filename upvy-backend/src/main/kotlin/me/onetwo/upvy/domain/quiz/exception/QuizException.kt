package me.onetwo.upvy.domain.quiz.exception

/**
 * 퀴즈 도메인 예외
 *
 * 퀴즈 관련 비즈니스 로직에서 발생하는 모든 예외의 기본 클래스입니다.
 */
sealed class QuizException(message: String) : RuntimeException(message) {

    /**
     * 퀴즈를 찾을 수 없는 경우
     *
     * HTTP 상태 코드: 404 Not Found
     *
     * @param quizId 퀴즈 ID
     */
    class QuizNotFoundException(quizId: String) :
        QuizException("Quiz not found: $quizId")

    /**
     * 콘텐츠에 연결된 퀴즈를 찾을 수 없는 경우
     *
     * HTTP 상태 코드: 404 Not Found
     *
     * @param contentId 콘텐츠 ID
     */
    class QuizNotFoundForContentException(contentId: String) :
        QuizException("Quiz not found for content: $contentId")

    /**
     * 퀴즈 생성 실패
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param reason 실패 이유
     */
    class QuizCreationException(reason: String) :
        QuizException("Quiz creation failed: $reason")

    /**
     * 퀴즈 수정 실패
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param reason 실패 이유
     */
    class QuizUpdateException(reason: String) :
        QuizException("Quiz update failed: $reason")

    /**
     * 잘못된 퀴즈 데이터
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param reason 검증 실패 이유
     */
    class InvalidQuizDataException(reason: String) :
        QuizException("Invalid quiz data: $reason")

    /**
     * 퀴즈 시도 실패
     *
     * HTTP 상태 코드: 400 Bad Request
     *
     * @param reason 실패 이유
     */
    class QuizAttemptException(reason: String) :
        QuizException("Quiz attempt failed: $reason")

    /**
     * 퀴즈 삭제 실패
     *
     * HTTP 상태 코드: 403 Forbidden
     *
     * @param reason 실패 이유
     */
    class QuizDeletionException(reason: String) :
        QuizException("Quiz deletion failed: $reason")

    /**
     * 이미 존재하는 퀴즈
     *
     * HTTP 상태 코드: 409 Conflict
     *
     * @param contentId 콘텐츠 ID
     */
    class QuizAlreadyExistsException(contentId: String) :
        QuizException("Quiz already exists for content: $contentId")
}
