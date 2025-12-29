package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.quiz.model.QuizAttemptAnswer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 퀴즈 시도 답변 레포지토리 인터페이스 (Reactive)
 *
 * 퀴즈 시도 시 선택한 보기 데이터베이스 CRUD를 담당합니다.
 */
interface QuizAttemptAnswerRepository {

    /**
     * 퀴즈 시도 답변을 저장합니다.
     *
     * @param answer 저장할 답변
     * @return 저장된 답변 (Mono)
     */
    fun save(answer: QuizAttemptAnswer): Mono<QuizAttemptAnswer>

    /**
     * 여러 답변을 일괄 저장합니다.
     *
     * @param answers 저장할 답변 목록
     * @return 저장된 답변 목록 (Flux)
     */
    fun saveAll(answers: List<QuizAttemptAnswer>): Flux<QuizAttemptAnswer>

    /**
     * 특정 시도에 대한 모든 답변을 조회합니다.
     *
     * @param attemptId 시도 ID
     * @return 답변 목록 (사용자가 선택한 보기들)
     */
    fun findByAttemptId(attemptId: UUID): Flux<QuizAttemptAnswer>

    /**
     * 특정 시도의 답변 개수를 조회합니다.
     *
     * @param attemptId 시도 ID
     * @return 선택한 보기 개수
     */
    fun countByAttemptId(attemptId: UUID): Mono<Int>

    /**
     * 특정 시도의 모든 답변을 삭제합니다 (Hard Delete).
     * 시도 자체가 삭제될 때 Cascade로 처리되지만, 명시적 삭제가 필요할 수 있음.
     *
     * @param attemptId 시도 ID
     * @return 완료 신호 (Mono<Void>)
     */
    fun deleteByAttemptId(attemptId: UUID): Mono<Void>

    /**
     * 특정 퀴즈의 각 보기별 선택 횟수를 한 번에 조회합니다.
     * N+1 문제를 방지하기 위해 GROUP BY를 사용하여 효율적으로 집계합니다.
     *
     * @param quizId 퀴즈 ID
     * @return 보기 ID별 선택 횟수 Map (Mono)
     */
    fun getSelectionCountsByQuizId(quizId: UUID): Mono<Map<UUID, Int>>
}
