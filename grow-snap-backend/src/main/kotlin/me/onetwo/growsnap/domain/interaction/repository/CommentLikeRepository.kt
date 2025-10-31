package me.onetwo.growsnap.domain.interaction.repository

import me.onetwo.growsnap.domain.interaction.model.CommentLike
import java.util.UUID

/**
 * 댓글 좋아요 레포지토리 인터페이스
 *
 * 사용자의 댓글 좋아요 상태를 관리합니다.
 * JOOQ를 사용하여 데이터베이스 CRUD 작업을 수행합니다.
 * Reactive 변환은 Service 계층에서 처리합니다.
 */
interface CommentLikeRepository {

    /**
     * 좋아요 생성
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 생성된 좋아요 (실패 시 null)
     */
    fun save(userId: UUID, commentId: UUID): CommentLike?

    /**
     * 좋아요 삭제 (Soft Delete)
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     */
    fun delete(userId: UUID, commentId: UUID)

    /**
     * 좋아요 존재 여부 확인
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 좋아요 여부 (true: 좋아요, false: 좋아요 안 함)
     */
    fun exists(userId: UUID, commentId: UUID): Boolean

    /**
     * 사용자의 좋아요 조회
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 좋아요 (없으면 null)
     */
    fun findByUserIdAndCommentId(userId: UUID, commentId: UUID): CommentLike?

    /**
     * 댓글의 좋아요 수 조회
     *
     * @param commentId 댓글 ID
     * @return 좋아요 수
     */
    fun countByCommentId(commentId: UUID): Int

    /**
     * 여러 댓글의 좋아요 수 일괄 조회 (N+1 문제 해결)
     *
     * @param commentIds 댓글 ID 목록
     * @return 댓글 ID를 키로, 좋아요 수를 값으로 하는 Map
     */
    fun countByCommentIds(commentIds: List<UUID>): Map<UUID, Int>

    /**
     * 사용자가 좋아요한 댓글 ID 목록 조회 (N+1 문제 해결)
     *
     * @param userId 사용자 ID
     * @param commentIds 댓글 ID 목록
     * @return 사용자가 좋아요한 댓글 ID 집합
     */
    fun findLikedCommentIds(userId: UUID, commentIds: List<UUID>): Set<UUID>
}
