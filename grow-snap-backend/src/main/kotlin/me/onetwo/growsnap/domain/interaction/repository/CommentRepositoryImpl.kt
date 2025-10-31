package me.onetwo.growsnap.domain.interaction.repository

import me.onetwo.growsnap.domain.interaction.model.Comment
import me.onetwo.growsnap.jooq.generated.tables.Comments.Companion.COMMENTS
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

/**
 * 댓글 레포지토리 구현체
 *
 * JOOQ를 사용하여 comments 테이블에 접근합니다.
 * Reactive 변환은 Service 계층에서 처리합니다.
 *
 * @property dslContext JOOQ DSL Context
 */
@Repository
class CommentRepositoryImpl(
    private val dslContext: DSLContext
) : CommentRepository {

    override fun save(comment: Comment): Comment? {
        val now = LocalDateTime.now()
        val commentId = comment.id ?: UUID.randomUUID()

        dslContext
            .insertInto(COMMENTS)
            .set(COMMENTS.ID, commentId.toString())
            .set(COMMENTS.CONTENT_ID, comment.contentId.toString())
            .set(COMMENTS.USER_ID, comment.userId.toString())
            .set(COMMENTS.PARENT_COMMENT_ID, comment.parentCommentId?.toString())
            .set(COMMENTS.CONTENT, comment.content)
            .set(COMMENTS.CREATED_AT, now)
            .set(COMMENTS.CREATED_BY, comment.userId.toString())
            .set(COMMENTS.UPDATED_AT, now)
            .set(COMMENTS.UPDATED_BY, comment.userId.toString())
            .execute()

        return dslContext
            .select(
                COMMENTS.ID,
                COMMENTS.CONTENT_ID,
                COMMENTS.USER_ID,
                COMMENTS.PARENT_COMMENT_ID,
                COMMENTS.CONTENT,
                COMMENTS.CREATED_AT,
                COMMENTS.CREATED_BY,
                COMMENTS.UPDATED_AT,
                COMMENTS.UPDATED_BY,
                COMMENTS.DELETED_AT
            )
            .from(COMMENTS)
            .where(COMMENTS.ID.eq(commentId.toString()))
            .fetchOne()
            ?.let { recordToComment(it) }
    }

    override fun findById(commentId: UUID): Comment? {
        return dslContext
            .select(
                COMMENTS.ID,
                COMMENTS.CONTENT_ID,
                COMMENTS.USER_ID,
                COMMENTS.PARENT_COMMENT_ID,
                COMMENTS.CONTENT,
                COMMENTS.CREATED_AT,
                COMMENTS.CREATED_BY,
                COMMENTS.UPDATED_AT,
                COMMENTS.UPDATED_BY,
                COMMENTS.DELETED_AT
            )
            .from(COMMENTS)
            .where(COMMENTS.ID.eq(commentId.toString()))
            .and(COMMENTS.DELETED_AT.isNull)
            .fetchOne()
            ?.let { recordToComment(it) }
    }

    override fun findByContentId(contentId: UUID): List<Comment> {
        return dslContext
            .select(
                COMMENTS.ID,
                COMMENTS.CONTENT_ID,
                COMMENTS.USER_ID,
                COMMENTS.PARENT_COMMENT_ID,
                COMMENTS.CONTENT,
                COMMENTS.CREATED_AT,
                COMMENTS.CREATED_BY,
                COMMENTS.UPDATED_AT,
                COMMENTS.UPDATED_BY,
                COMMENTS.DELETED_AT
            )
            .from(COMMENTS)
            .where(COMMENTS.CONTENT_ID.eq(contentId.toString()))
            .and(COMMENTS.DELETED_AT.isNull)
            .orderBy(COMMENTS.CREATED_AT.asc())
            .fetch()
            .map { recordToComment(it) }
    }

    override fun findByParentCommentId(parentCommentId: UUID): List<Comment> {
        return dslContext
            .select(
                COMMENTS.ID,
                COMMENTS.CONTENT_ID,
                COMMENTS.USER_ID,
                COMMENTS.PARENT_COMMENT_ID,
                COMMENTS.CONTENT,
                COMMENTS.CREATED_AT,
                COMMENTS.CREATED_BY,
                COMMENTS.UPDATED_AT,
                COMMENTS.UPDATED_BY,
                COMMENTS.DELETED_AT
            )
            .from(COMMENTS)
            .where(COMMENTS.PARENT_COMMENT_ID.eq(parentCommentId.toString()))
            .and(COMMENTS.DELETED_AT.isNull)
            .orderBy(COMMENTS.CREATED_AT.asc())
            .fetch()
            .map { recordToComment(it) }
    }

    override fun findTopLevelCommentsByContentId(contentId: UUID, cursor: UUID?, limit: Int): List<Comment> {
        val query = dslContext
            .select(
                COMMENTS.ID,
                COMMENTS.CONTENT_ID,
                COMMENTS.USER_ID,
                COMMENTS.PARENT_COMMENT_ID,
                COMMENTS.CONTENT,
                COMMENTS.CREATED_AT,
                COMMENTS.CREATED_BY,
                COMMENTS.UPDATED_AT,
                COMMENTS.UPDATED_BY,
                COMMENTS.DELETED_AT
            )
            .from(COMMENTS)
            .where(COMMENTS.CONTENT_ID.eq(contentId.toString()))
            .and(COMMENTS.PARENT_COMMENT_ID.isNull)
            .and(COMMENTS.DELETED_AT.isNull)

        // Cursor가 있으면 해당 댓글 이후의 데이터만 조회
        if (cursor != null) {
            val cursorComment = findById(cursor)
            if (cursorComment != null) {
                query.and(COMMENTS.CREATED_AT.gt(cursorComment.createdAt))
            }
        }

        return query
            .orderBy(COMMENTS.CREATED_AT.asc())
            .limit(limit + 1) // hasNext 확인을 위해 +1 조회
            .fetch()
            .map { recordToComment(it) }
    }

    override fun findRepliesByParentCommentId(parentCommentId: UUID, cursor: UUID?, limit: Int): List<Comment> {
        val query = dslContext
            .select(
                COMMENTS.ID,
                COMMENTS.CONTENT_ID,
                COMMENTS.USER_ID,
                COMMENTS.PARENT_COMMENT_ID,
                COMMENTS.CONTENT,
                COMMENTS.CREATED_AT,
                COMMENTS.CREATED_BY,
                COMMENTS.UPDATED_AT,
                COMMENTS.UPDATED_BY,
                COMMENTS.DELETED_AT
            )
            .from(COMMENTS)
            .where(COMMENTS.PARENT_COMMENT_ID.eq(parentCommentId.toString()))
            .and(COMMENTS.DELETED_AT.isNull)

        // Cursor가 있으면 해당 댓글 이후의 데이터만 조회
        if (cursor != null) {
            val cursorComment = findById(cursor)
            if (cursorComment != null) {
                query.and(COMMENTS.CREATED_AT.gt(cursorComment.createdAt))
            }
        }

        return query
            .orderBy(COMMENTS.CREATED_AT.asc())
            .limit(limit + 1) // hasNext 확인을 위해 +1 조회
            .fetch()
            .map { recordToComment(it) }
    }

    override fun countRepliesByParentCommentId(parentCommentId: UUID): Int {
        return dslContext
            .selectCount()
            .from(COMMENTS)
            .where(COMMENTS.PARENT_COMMENT_ID.eq(parentCommentId.toString()))
            .and(COMMENTS.DELETED_AT.isNull)
            .fetchOne(0, Int::class.java) ?: 0
    }

    override fun delete(commentId: UUID, userId: UUID) {
        val now = LocalDateTime.now()

        dslContext
            .update(COMMENTS)
            .set(COMMENTS.DELETED_AT, now)
            .set(COMMENTS.UPDATED_AT, now)
            .set(COMMENTS.UPDATED_BY, userId.toString())
            .where(COMMENTS.ID.eq(commentId.toString()))
            .and(COMMENTS.DELETED_AT.isNull)
            .execute()
    }

    /**
     * JOOQ Record를 Comment 엔티티로 변환
     *
     * @param record JOOQ Record
     * @return Comment 엔티티
     */
    private fun recordToComment(record: Record): Comment {
        return Comment(
            id = UUID.fromString(record.getValue(COMMENTS.ID)),
            contentId = UUID.fromString(record.getValue(COMMENTS.CONTENT_ID)),
            userId = UUID.fromString(record.getValue(COMMENTS.USER_ID)),
            parentCommentId = record.getValue(COMMENTS.PARENT_COMMENT_ID)?.let { UUID.fromString(it) },
            content = record.getValue(COMMENTS.CONTENT)!!,
            createdAt = record.getValue(COMMENTS.CREATED_AT)!!,
            createdBy = record.getValue(COMMENTS.CREATED_BY)?.let { UUID.fromString(it) },
            updatedAt = record.getValue(COMMENTS.UPDATED_AT)!!,
            updatedBy = record.getValue(COMMENTS.UPDATED_BY)?.let { UUID.fromString(it) },
            deletedAt = record.getValue(COMMENTS.DELETED_AT)
        )
    }
}
