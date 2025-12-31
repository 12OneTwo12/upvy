package me.onetwo.upvy.domain.quiz.repository

import me.onetwo.upvy.domain.quiz.dto.QuizMetadataResponse
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

    /**
     * 여러 콘텐츠의 퀴즈 메타데이터를 배치 조회합니다 (N+1 문제 방지).
     *
     * 각 콘텐츠에 대해:
     * - 퀴즈가 있으면 QuizMetadataResponse 반환
     * - 퀴즈가 없으면 Map에 해당 contentId 키가 없음
     *
     * @param contentIds 콘텐츠 ID 목록
     * @param userId 사용자 ID (시도 횟수 조회용, nullable)
     * @return contentId를 키로 하는 QuizMetadataResponse 맵
     */
    fun findQuizMetadataByContentIds(contentIds: List<UUID>, userId: UUID?): Mono<Map<UUID, QuizMetadataResponse>>
}
