package me.onetwo.growsnap.domain.interaction.repository

import me.onetwo.growsnap.domain.interaction.model.CommentLike
import me.onetwo.growsnap.jooq.generated.tables.UserCommentLikes.Companion.USER_COMMENT_LIKES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

/**
 * 댓글 좋아요 레포지토리 구현체
 *
 * JOOQ를 사용하여 user_comment_likes 테이블에 접근합니다.
 * Reactive 변환은 Service 계층에서 처리합니다.
 *
 * @property dslContext JOOQ DSLContext
 */
@Repository
class CommentLikeRepositoryImpl(
    private val dslContext: DSLContext
) : CommentLikeRepository {

    /**
     * 댓글 좋아요 생성
     *
     * ### 처리 흐름
     * 1. user_comment_likes 테이블에 INSERT
     * 2. created_at, created_by, updated_at, updated_by 자동 설정
     *
     * ### 비즈니스 규칙
     * - UNIQUE 제약조건 (user_id, comment_id)으로 중복 방지
     * - 이미 좋아요가 있으면 DuplicateKeyException 발생
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 생성된 댓글 좋아요
     */
    override fun save(userId: UUID, commentId: UUID): CommentLike? {
        val now = LocalDateTime.now()

        return dslContext
            .insertInto(USER_COMMENT_LIKES)
            .set(USER_COMMENT_LIKES.USER_ID, userId.toString())
            .set(USER_COMMENT_LIKES.COMMENT_ID, commentId.toString())
            .set(USER_COMMENT_LIKES.CREATED_AT, now)
            .set(USER_COMMENT_LIKES.CREATED_BY, userId.toString())
            .set(USER_COMMENT_LIKES.UPDATED_AT, now)
            .set(USER_COMMENT_LIKES.UPDATED_BY, userId.toString())
            .returning()
            .fetchOne()
            ?.let { toCommentLike(it) }
    }

    /**
     * 댓글 좋아요 삭제 (Soft Delete)
     *
     * ### 처리 흐름
     * 1. user_comment_likes 테이블에서 deleted_at 업데이트
     * 2. updated_at, updated_by 갱신
     *
     * ### 비즈니스 규칙
     * - 물리적 삭제 금지, 논리적 삭제만 허용
     * - 이미 삭제된 데이터는 제외 (deleted_at IS NULL 조건)
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     */
    override fun delete(userId: UUID, commentId: UUID) {
        val now = LocalDateTime.now()

        dslContext
            .update(USER_COMMENT_LIKES)
            .set(USER_COMMENT_LIKES.DELETED_AT, now)
            .set(USER_COMMENT_LIKES.UPDATED_AT, now)
            .set(USER_COMMENT_LIKES.UPDATED_BY, userId.toString())
            .where(USER_COMMENT_LIKES.USER_ID.eq(userId.toString()))
            .and(USER_COMMENT_LIKES.COMMENT_ID.eq(commentId.toString()))
            .and(USER_COMMENT_LIKES.DELETED_AT.isNull)
            .execute()
    }

    /**
     * 댓글 좋아요 존재 여부 확인
     *
     * deleted_at이 NULL인 레코드만 확인합니다.
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 좋아요 여부
     */
    override fun exists(userId: UUID, commentId: UUID): Boolean {
        return dslContext
            .selectCount()
            .from(USER_COMMENT_LIKES)
            .where(USER_COMMENT_LIKES.USER_ID.eq(userId.toString()))
            .and(USER_COMMENT_LIKES.COMMENT_ID.eq(commentId.toString()))
            .and(USER_COMMENT_LIKES.DELETED_AT.isNull)
            .fetchOne(0, Int::class.java) ?: 0 > 0
    }

    /**
     * 사용자의 댓글 좋아요 조회
     *
     * deleted_at이 NULL인 레코드만 조회합니다.
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 (없으면 null)
     */
    override fun findByUserIdAndCommentId(userId: UUID, commentId: UUID): CommentLike? {
        return dslContext
            .select(
                USER_COMMENT_LIKES.ID,
                USER_COMMENT_LIKES.USER_ID,
                USER_COMMENT_LIKES.COMMENT_ID,
                USER_COMMENT_LIKES.CREATED_AT,
                USER_COMMENT_LIKES.CREATED_BY,
                USER_COMMENT_LIKES.UPDATED_AT,
                USER_COMMENT_LIKES.UPDATED_BY,
                USER_COMMENT_LIKES.DELETED_AT
            )
            .from(USER_COMMENT_LIKES)
            .where(USER_COMMENT_LIKES.USER_ID.eq(userId.toString()))
            .and(USER_COMMENT_LIKES.COMMENT_ID.eq(commentId.toString()))
            .and(USER_COMMENT_LIKES.DELETED_AT.isNull)
            .fetchOne()
            ?.let { toCommentLike(it) }
    }

    /**
     * 댓글의 좋아요 수 조회
     *
     * deleted_at이 NULL인 레코드만 카운트합니다.
     *
     * @param commentId 댓글 ID
     * @return 좋아요 수
     */
    override fun countByCommentId(commentId: UUID): Int {
        return dslContext
            .selectCount()
            .from(USER_COMMENT_LIKES)
            .where(USER_COMMENT_LIKES.COMMENT_ID.eq(commentId.toString()))
            .and(USER_COMMENT_LIKES.DELETED_AT.isNull)
            .fetchOne(0, Int::class.java) ?: 0
    }

    /**
     * JOOQ Record를 CommentLike 엔티티로 변환
     *
     * @param record JOOQ Record
     * @return CommentLike 엔티티
     */
    private fun toCommentLike(record: Record): CommentLike {
        return CommentLike(
            id = record.getValue(USER_COMMENT_LIKES.ID),
            userId = UUID.fromString(record.getValue(USER_COMMENT_LIKES.USER_ID)),
            commentId = UUID.fromString(record.getValue(USER_COMMENT_LIKES.COMMENT_ID)),
            createdAt = record.getValue(USER_COMMENT_LIKES.CREATED_AT)!!,
            createdBy = record.getValue(USER_COMMENT_LIKES.CREATED_BY)?.let { UUID.fromString(it) },
            updatedAt = record.getValue(USER_COMMENT_LIKES.UPDATED_AT)!!,
            updatedBy = record.getValue(USER_COMMENT_LIKES.UPDATED_BY)?.let { UUID.fromString(it) },
            deletedAt = record.getValue(USER_COMMENT_LIKES.DELETED_AT)
        )
    }
}
