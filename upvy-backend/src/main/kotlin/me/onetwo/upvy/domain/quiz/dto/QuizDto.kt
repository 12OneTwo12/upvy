package me.onetwo.upvy.domain.quiz.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

/**
 * 퀴즈 생성 요청 DTO
 *
 * 새로운 퀴즈를 생성할 때 사용합니다.
 * 퀴즈 문제와 보기를 함께 전달합니다.
 *
 * @property question 퀴즈 질문 (최대 200자)
 * @property allowMultipleAnswers 복수 정답 허용 여부
 * @property options 퀴즈 보기 목록 (최소 2개 이상)
 */
data class QuizCreateRequest(
    @field:NotBlank(message = "질문은 필수입니다")
    @field:Size(max = 200, message = "질문은 최대 200자입니다")
    val question: String,

    val allowMultipleAnswers: Boolean = false,

    @field:NotEmpty(message = "보기는 최소 2개 이상 필요합니다")
    @field:Size(min = 2, message = "보기는 최소 2개 이상 필요합니다")
    @field:Valid
    val options: List<QuizOptionCreateRequest>
)

/**
 * 퀴즈 보기 생성 요청 DTO
 *
 * @property optionText 보기 텍스트 (최대 100자)
 * @property isCorrect 정답 여부
 */
data class QuizOptionCreateRequest(
    @field:NotBlank(message = "보기 텍스트는 필수입니다")
    @field:Size(max = 100, message = "보기 텍스트는 최대 100자입니다")
    val optionText: String,

    val isCorrect: Boolean
)

/**
 * 퀴즈 수정 요청 DTO
 *
 * @property question 수정할 질문 (최대 200자)
 * @property allowMultipleAnswers 복수 정답 허용 여부
 * @property options 수정할 보기 목록
 */
data class QuizUpdateRequest(
    @field:NotBlank(message = "질문은 필수입니다")
    @field:Size(max = 200, message = "질문은 최대 200자입니다")
    val question: String,

    val allowMultipleAnswers: Boolean = false,

    @field:NotEmpty(message = "보기는 최소 2개 이상 필요합니다")
    @field:Size(min = 2, message = "보기는 최소 2개 이상 필요합니다")
    @field:Valid
    val options: List<QuizOptionCreateRequest>
)

/**
 * 퀴즈 조회 응답 DTO
 *
 * @property id 퀴즈 ID
 * @property contentId 연관 콘텐츠 ID
 * @property question 퀴즈 질문
 * @property allowMultipleAnswers 복수 정답 허용 여부
 * @property options 퀴즈 보기 목록
 * @property userAttemptCount 현재 사용자의 시도 횟수 (null이면 로그인하지 않았거나 시도 안함)
 * @property totalAttempts 전체 시도 횟수
 */
data class QuizResponse(
    val id: String,
    val contentId: String,
    val question: String,
    val allowMultipleAnswers: Boolean,
    val options: List<QuizOptionResponse>,
    val userAttemptCount: Int? = null,
    val totalAttempts: Int = 0
)

/**
 * 퀴즈 보기 응답 DTO
 *
 * @property id 보기 ID
 * @property optionText 보기 텍스트
 * @property displayOrder 표시 순서
 * @property selectionCount 이 보기를 선택한 횟수 (모든 시도 포함)
 * @property selectionPercentage 이 보기를 선택한 비율 (0.0 ~ 100.0)
 * @property isCorrect 정답 여부 (사용자가 이미 풀었을 경우에만 노출)
 */
data class QuizOptionResponse(
    val id: String,
    val optionText: String,
    val displayOrder: Int,
    val selectionCount: Int = 0,
    val selectionPercentage: Double = 0.0,
    val isCorrect: Boolean? = null
)

/**
 * 퀴즈 시도 요청 DTO
 *
 * @property selectedOptionIds 선택한 보기 ID 목록
 */
data class QuizAttemptRequest(
    @field:NotEmpty(message = "최소 1개 이상의 보기를 선택해야 합니다")
    val selectedOptionIds: List<String>
)

/**
 * 퀴즈 시도 응답 DTO
 *
 * 퀴즈 제출 후 즉시 정답 여부와 통계를 반환합니다.
 *
 * @property attemptId 시도 ID
 * @property quizId 퀴즈 ID
 * @property isCorrect 정답 여부
 * @property attemptNumber 시도 번호 (1, 2, 3, ...)
 * @property options 정답이 포함된 보기 목록 (통계 포함)
 */
data class QuizAttemptResponse(
    val attemptId: String,
    val quizId: String,
    val isCorrect: Boolean,
    val attemptNumber: Int,
    val options: List<QuizOptionResponse>
)

/**
 * 사용자 퀴즈 시도 기록 응답 DTO
 *
 * @property attempts 시도 기록 목록 (최신순)
 */
data class UserQuizAttemptsResponse(
    val attempts: List<UserQuizAttemptDetail>
)

/**
 * 사용자 퀴즈 시도 상세 정보
 *
 * @property attemptId 시도 ID
 * @property attemptNumber 시도 번호
 * @property isCorrect 정답 여부
 * @property selectedOptions 선택한 보기 ID 목록
 * @property attemptedAt 시도 시각 (ISO-8601 형식)
 */
data class UserQuizAttemptDetail(
    val attemptId: String,
    val attemptNumber: Int,
    val isCorrect: Boolean,
    val selectedOptions: List<String>,
    val attemptedAt: String
)

/**
 * 퀴즈 통계 응답 DTO
 *
 * @property quizId 퀴즈 ID
 * @property totalAttempts 전체 시도 횟수
 * @property uniqueUsers 참여한 고유 사용자 수
 * @property options 보기별 통계
 */
data class QuizStatsResponse(
    val quizId: String,
    val totalAttempts: Int,
    val uniqueUsers: Int,
    val options: List<QuizOptionStatsResponse>
)

/**
 * 퀴즈 보기 통계 응답 DTO
 *
 * @property optionId 보기 ID
 * @property optionText 보기 텍스트
 * @property selectionCount 선택 횟수
 * @property selectionPercentage 선택 비율 (0.0 ~ 100.0)
 * @property isCorrect 정답 여부
 */
data class QuizOptionStatsResponse(
    val optionId: String,
    val optionText: String,
    val selectionCount: Int,
    val selectionPercentage: Double,
    val isCorrect: Boolean
)
