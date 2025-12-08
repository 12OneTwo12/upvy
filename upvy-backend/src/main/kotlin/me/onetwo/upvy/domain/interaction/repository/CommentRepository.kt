package me.onetwo.upvy.domain.interaction.repository

import me.onetwo.upvy.domain.interaction.model.Comment
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 댓글 Repository 인터페이스 (Reactive)
 *
 * 댓글 CRUD 작업을 관리합니다.
 * R2DBC를 통해 완전한 Non-blocking 처리를 지원합니다.
 * 모든 메서드는 Mono 또는 Flux를 반환합니다.
 */
interface CommentRepository {

    /**
     * 댓글 저장
     *
     * @param comment 저장할 댓글
     * @return 저장된 댓글 (Mono)
     */
    fun save(comment: Comment): Mono<Comment>

    /**
     * 댓글 ID로 조회
     *
     * @param commentId 댓글 ID
     * @return 댓글 (없으면 empty Mono)
     */
    fun findById(commentId: UUID): Mono<Comment>

    /**
     * 콘텐츠의 모든 댓글 조회 (삭제되지 않은 것만)
     *
     * @param contentId 콘텐츠 ID
     * @return 댓글 목록 (Flux)
     */
    fun findByContentId(contentId: UUID): Flux<Comment>

    /**
     * 부모 댓글 ID로 대댓글 조회
     *
     * @param parentCommentId 부모 댓글 ID
     * @return 대댓글 목록 (Flux)
     */
    fun findByParentCommentId(parentCommentId: UUID): Flux<Comment>

    /**
     * 콘텐츠의 최상위 댓글 조회 (Cursor 기반 페이징)
     *
     * @param contentId 콘텐츠 ID
     * @param cursor 커서 (댓글 ID, null이면 처음부터)
     * @param limit 조회 개수
     * @return 댓글 목록 (Flux)
     */
    fun findTopLevelCommentsByContentId(contentId: UUID, cursor: UUID?, limit: Int): Flux<Comment>

    /**
     * 대댓글 조회 (Cursor 기반 페이징)
     *
     * @param parentCommentId 부모 댓글 ID
     * @param cursor 커서 (댓글 ID, null이면 처음부터)
     * @param limit 조회 개수
     * @return 대댓글 목록 (Flux)
     */
    fun findRepliesByParentCommentId(parentCommentId: UUID, cursor: UUID?, limit: Int): Flux<Comment>

    /**
     * 댓글의 대댓글 개수 조회
     *
     * @param parentCommentId 부모 댓글 ID
     * @return 대댓글 개수 (Mono)
     */
    fun countRepliesByParentCommentId(parentCommentId: UUID): Mono<Int>

    /**
     * 여러 댓글의 대댓글 개수 일괄 조회 (N+1 문제 해결)
     *
     * @param parentCommentIds 부모 댓글 ID 목록
     * @return 부모 댓글 ID를 키로, 대댓글 개수를 값으로 하는 Map (Mono)
     */
    fun countRepliesByParentCommentIds(parentCommentIds: List<UUID>): Mono<Map<UUID, Int>>

    /**
     * 댓글 삭제 (Soft Delete)
     *
     * @param commentId 댓글 ID
     * @param userId 삭제하는 사용자 ID
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    fun delete(commentId: UUID, userId: UUID): Mono<Void>
}
