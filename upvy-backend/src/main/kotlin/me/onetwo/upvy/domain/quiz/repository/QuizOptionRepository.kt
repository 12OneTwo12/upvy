package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.quiz.model.QuizOption
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 퀴즈 보기 레포지토리 인터페이스 (Reactive)
 *
 * 퀴즈 보기 데이터베이스 CRUD를 담당합니다.
 */
interface QuizOptionRepository {

    /**
     * 퀴즈 보기를 저장합니다.
     *
     * @param option 저장할 보기
     * @return 저장된 보기 (Mono)
     */
    fun save(option: QuizOption): Mono<QuizOption>

    /**
     * 퀴즈 ID로 모든 보기를 조회합니다.
     *
     * @param quizId 퀴즈 ID
     * @return 보기 목록 (displayOrder 순으로 정렬)
     */
    fun findByQuizId(quizId: UUID): Flux<QuizOption>

    /**
     * 보기 ID로 조회합니다.
     *
     * @param optionId 보기 ID
     * @return 조회된 보기 (없으면 empty Mono)
     */
    fun findById(optionId: UUID): Mono<QuizOption>

    /**
     * 여러 보기 ID로 조회합니다.
     *
     * @param optionIds 보기 ID 목록
     * @return 보기 목록
     */
    fun findByIdIn(optionIds: List<UUID>): Flux<QuizOption>

    /**
     * 퀴즈의 모든 보기를 삭제합니다 (Soft Delete).
     *
     * @param quizId 퀴즈 ID
     * @param deletedBy 삭제를 수행한 사용자 ID
     * @return 완료 신호 (Mono<Void>)
     */
    fun deleteByQuizId(quizId: UUID, deletedBy: UUID): Mono<Void>

    /**
     * 퀴즈의 보기 개수를 조회합니다.
     *
     * @param quizId 퀴즈 ID
     * @return 보기 개수
     */
    fun countByQuizId(quizId: UUID): Mono<Int>
}
