package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.quiz.model.Quiz
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 퀴즈 레포지토리 인터페이스 (Reactive)
 *
 * 퀴즈 데이터베이스 CRUD를 담당합니다.
 * R2DBC를 통해 완전한 Non-blocking 처리를 지원합니다.
 */
interface QuizRepository {

    /**
     * 퀴즈를 저장합니다.
     *
     * @param quiz 저장할 퀴즈
     * @return 저장된 퀴즈 (Mono)
     */
    fun save(quiz: Quiz): Mono<Quiz>

    /**
     * 퀴즈를 ID로 조회합니다.
     *
     * @param quizId 퀴즈 ID
     * @return 조회된 퀴즈 (없으면 empty Mono)
     */
    fun findById(quizId: UUID): Mono<Quiz>

    /**
     * 콘텐츠 ID로 퀴즈를 조회합니다 (1:1 관계).
     *
     * @param contentId 콘텐츠 ID
     * @return 조회된 퀴즈 (없으면 empty Mono)
     */
    fun findByContentId(contentId: UUID): Mono<Quiz>

    /**
     * 퀴즈를 수정합니다.
     *
     * @param quiz 수정할 퀴즈 (ID 필수)
     * @return 수정된 퀴즈 (Mono)
     */
    fun update(quiz: Quiz): Mono<Quiz>

    /**
     * 퀴즈를 삭제합니다 (Soft Delete).
     *
     * @param quizId 퀴즈 ID
     * @param deletedBy 삭제를 수행한 사용자 ID
     * @return 완료 신호 (Mono<Void>)
     */
    fun delete(quizId: UUID, deletedBy: UUID): Mono<Void>

    /**
     * 콘텐츠에 퀴즈가 존재하는지 확인합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 존재 여부 (Mono<Boolean>)
     */
    fun existsByContentId(contentId: UUID): Mono<Boolean>
}
