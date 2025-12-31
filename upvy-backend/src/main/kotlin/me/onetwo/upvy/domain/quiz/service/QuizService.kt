package me.onetwo.upvy.domain.quiz.service

import me.onetwo.upvy.domain.quiz.dto.QuizAttemptRequest
import me.onetwo.upvy.domain.quiz.dto.QuizAttemptResponse
import me.onetwo.upvy.domain.quiz.dto.QuizCreateRequest
import me.onetwo.upvy.domain.quiz.dto.QuizResponse
import me.onetwo.upvy.domain.quiz.dto.QuizStatsResponse
import me.onetwo.upvy.domain.quiz.dto.QuizUpdateRequest
import me.onetwo.upvy.domain.quiz.dto.UserQuizAttemptsResponse
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 퀴즈 서비스 인터페이스
 *
 * 퀴즈 생성, 조회, 수정, 삭제 및 퀴즈 시도 기능을 제공합니다.
 */
interface QuizService {

    /**
     * 콘텐츠에 퀴즈를 생성합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param request 퀴즈 생성 요청
     * @param createdBy 생성자 ID
     * @return 생성된 퀴즈 응답
     */
    fun createQuiz(contentId: UUID, request: QuizCreateRequest, createdBy: UUID): Mono<QuizResponse>

    /**
     * 콘텐츠의 퀴즈를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param userId 조회하는 사용자 ID (nullable - 비로그인 시 null)
     * @return 퀴즈 응답 (통계 포함, 사용자가 풀지 않았으면 정답 숨김)
     */
    fun getQuizByContentId(contentId: UUID, userId: UUID?): Mono<QuizResponse>

    /**
     * 콘텐츠의 퀴즈를 수정합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param request 퀴즈 수정 요청
     * @param updatedBy 수정자 ID
     * @return 수정된 퀴즈 응답
     */
    fun updateQuiz(contentId: UUID, request: QuizUpdateRequest, updatedBy: UUID): Mono<QuizResponse>

    /**
     * 콘텐츠의 퀴즈를 삭제합니다 (Soft Delete).
     *
     * @param contentId 콘텐츠 ID
     * @param deletedBy 삭제자 ID
     * @return 완료 신호 (Mono<Void>)
     */
    fun deleteQuiz(contentId: UUID, deletedBy: UUID): Mono<Void>

    /**
     * 퀴즈 시도를 제출합니다.
     *
     * @param quizId 퀴즈 ID
     * @param userId 사용자 ID
     * @param request 퀴즈 시도 요청 (선택한 보기 목록)
     * @return 퀴즈 시도 응답 (정답 여부 + 통계)
     */
    fun submitQuizAttempt(quizId: UUID, userId: UUID, request: QuizAttemptRequest): Mono<QuizAttemptResponse>

    /**
     * 사용자의 퀴즈 시도 기록을 조회합니다.
     *
     * @param quizId 퀴즈 ID
     * @param userId 사용자 ID
     * @return 사용자 퀴즈 시도 기록 응답
     */
    fun getUserQuizAttempts(quizId: UUID, userId: UUID): Mono<UserQuizAttemptsResponse>

    /**
     * 퀴즈 통계를 조회합니다.
     *
     * @param quizId 퀴즈 ID
     * @return 퀴즈 통계 응답
     */
    fun getQuizStats(quizId: UUID): Mono<QuizStatsResponse>
}
