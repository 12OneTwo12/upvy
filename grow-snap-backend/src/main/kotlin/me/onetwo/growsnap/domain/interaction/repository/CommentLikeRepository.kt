package me.onetwo.growsnap.domain.interaction.repository

import me.onetwo.growsnap.domain.interaction.model.CommentLike
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 댓글 좋아요 레포지토리 인터페이스 (Reactive)
 *
 * 사용자의 댓글 좋아요 상태를 관리합니다.
 * JOOQ를 사용하여 SQL을 생성하고, R2DBC를 통해 실행합니다.
 * 모든 메서드는 Mono를 반환하여 완전한 Non-blocking 처리를 지원합니다.
 */
interface CommentLikeRepository {

    /**
     * 좋아요 생성
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 생성된 좋아요 (Mono)
     */
    fun save(userId: UUID, commentId: UUID): Mono<CommentLike>

    /**
     * 좋아요 삭제 (Soft Delete)
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    fun delete(userId: UUID, commentId: UUID): Mono<Void>

    /**
     * 좋아요 존재 여부 확인
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 좋아요 여부 (true: 좋아요, false: 좋아요 안 함)
     */
    fun exists(userId: UUID, commentId: UUID): Mono<Boolean>

    /**
     * 사용자의 좋아요 조회
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 좋아요 (없으면 empty Mono)
     */
    fun findByUserIdAndCommentId(userId: UUID, commentId: UUID): Mono<CommentLike>

    /**
     * 댓글의 좋아요 수 조회
     *
     * @param commentId 댓글 ID
     * @return 좋아요 수 (Mono)
     */
    fun countByCommentId(commentId: UUID): Mono<Int>

    /**
     * 여러 댓글의 좋아요 수 일괄 조회 (N+1 문제 해결)
     *
     * @param commentIds 댓글 ID 목록
     * @return 댓글 ID를 키로, 좋아요 수를 값으로 하는 Map (Mono)
     */
    fun countByCommentIds(commentIds: List<UUID>): Mono<Map<UUID, Int>>

    /**
     * 사용자가 좋아요한 댓글 ID 목록 조회 (N+1 문제 해결)
     *
     * @param userId 사용자 ID
     * @param commentIds 댓글 ID 목록
     * @return 사용자가 좋아요한 댓글 ID 집합 (Mono)
     */
    fun findLikedCommentIds(userId: UUID, commentIds: List<UUID>): Mono<Set<UUID>>
}
