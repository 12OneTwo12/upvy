package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.quiz.model.QuizAttempt
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 퀴즈 시도 레포지토리 인터페이스 (Reactive)
 *
 * 퀴즈 시도 기록 데이터베이스 CRUD를 담당합니다.
 */
interface QuizAttemptRepository {

    /**
     * 퀴즈 시도를 저장합니다.
     *
     * @param attempt 저장할 시도
     * @return 저장된 시도 (Mono)
     */
    fun save(attempt: QuizAttempt): Mono<QuizAttempt>

    /**
     * 시도 ID로 조회합니다.
     *
     * @param attemptId 시도 ID
     * @return 조회된 시도 (없으면 empty Mono)
     */
    fun findById(attemptId: UUID): Mono<QuizAttempt>

    /**
     * 사용자의 특정 퀴즈에 대한 모든 시도를 조회합니다.
     *
     * @param quizId 퀴즈 ID
     * @param userId 사용자 ID
     * @return 시도 목록 (최신순 정렬)
     */
    fun findByQuizIdAndUserId(quizId: UUID, userId: UUID): Flux<QuizAttempt>

    /**
     * 사용자의 특정 퀴즈에 대한 다음 시도 번호를 계산합니다.
     *
     * @param quizId 퀴즈 ID
     * @param userId 사용자 ID
     * @return 다음 시도 번호 (1부터 시작)
     */
    fun getNextAttemptNumber(quizId: UUID, userId: UUID): Mono<Int>

    /**
     * 퀴즈의 모든 시도 기록을 조회합니다 (통계용).
     *
     * @param quizId 퀴즈 ID
     * @return 모든 사용자의 시도 목록
     */
    fun findByQuizId(quizId: UUID): Flux<QuizAttempt>

    /**
     * 사용자의 특정 퀴즈 시도 개수를 조회합니다.
     *
     * @param quizId 퀴즈 ID
     * @param userId 사용자 ID
     * @return 시도 개수
     */
    fun countByQuizIdAndUserId(quizId: UUID, userId: UUID): Mono<Int>

    /**
     * 특정 퀴즈의 전체 시도 횟수를 조회합니다 (통계용).
     * N+1 문제를 방지하기 위해 단일 COUNT 쿼리로 조회합니다.
     *
     * @param quizId 퀴즈 ID
     * @return 전체 시도 횟수
     */
    fun countByQuizId(quizId: UUID): Mono<Int>

    /**
     * 특정 퀴즈를 시도한 고유 사용자 수를 조회합니다 (통계용).
     * N+1 문제를 방지하기 위해 단일 COUNT DISTINCT 쿼리로 조회합니다.
     *
     * @param quizId 퀴즈 ID
     * @return 고유 사용자 수
     */
    fun countDistinctUsersByQuizId(quizId: UUID): Mono<Int>
}
