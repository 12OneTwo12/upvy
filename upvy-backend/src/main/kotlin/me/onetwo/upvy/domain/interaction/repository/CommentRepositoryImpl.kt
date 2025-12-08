package me.onetwo.upvy.domain.interaction.repository

import me.onetwo.upvy.domain.interaction.model.Comment
import me.onetwo.upvy.jooq.generated.tables.references.COMMENTS
import me.onetwo.upvy.jooq.generated.tables.references.USER_COMMENT_LIKES
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 댓글 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class CommentRepositoryImpl(
    private val dslContext: DSLContext
) : CommentRepository {

    override fun save(comment: Comment): Mono<Comment> {
        val now = Instant.now()
        val commentId = comment.id ?: UUID.randomUUID()

        // MySQL R2DBC는 단일 자동 생성 값만 지원하므로, returningResult()를 제거하고
        // ID를 미리 생성하여 메모리에서 Comment 객체 구성
        return Mono.from(
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
        ).thenReturn(
            Comment(
                id = commentId,
                contentId = comment.contentId,
                userId = comment.userId,
                parentCommentId = comment.parentCommentId,
                content = comment.content,
                createdAt = now,
                createdBy = comment.userId.toString(),
                updatedAt = now,
                updatedBy = comment.userId.toString(),
                deletedAt = null
            )
        )
    }

    override fun findById(commentId: UUID): Mono<Comment> {
        return Mono.from(
            dslContext
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
        ).map { record -> recordToComment(record) }
    }

    override fun findByContentId(contentId: UUID): Flux<Comment> {
        return Flux.from(
            dslContext
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
        ).map { record -> recordToComment(record) }
    }

    override fun findByParentCommentId(parentCommentId: UUID): Flux<Comment> {
        return Flux.from(
            dslContext
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
        ).map { record -> recordToComment(record) }
    }

    override fun findTopLevelCommentsByContentId(contentId: UUID, cursor: UUID?, limit: Int): Flux<Comment> {
        // 댓글별 alias 테이블 (self-join용)
        val replies = COMMENTS.`as`("replies")

        // 인기 점수 계산: 좋아요 수 + 대댓글 수
        val likeCountField = DSL.countDistinct(USER_COMMENT_LIKES.ID)
        val replyCountField = DSL.countDistinct(replies.ID)
        val popularityScore = likeCountField.plus(replyCountField)

        val whereConditions = mutableListOf(
            COMMENTS.CONTENT_ID.eq(contentId.toString()),
            COMMENTS.PARENT_COMMENT_ID.isNull,
            COMMENTS.DELETED_AT.isNull
        )

        // Cursor 기반 페이징: 커서 댓글의 인기 점수와 생성일을 기준으로 필터링
        return if (cursor != null) {
            // Cursor의 createdAt을 서브쿼리로 가져옴 (Instant 타입 변환 이슈 방지)
            val cursorCreatedAtSubquery = DSL.select(COMMENTS.CREATED_AT)
                .from(COMMENTS)
                .where(COMMENTS.ID.eq(cursor.toString()))

            // 커서 댓글의 인기 점수 계산
            Mono.from(
                dslContext
                    .selectCount()
                    .from(USER_COMMENT_LIKES)
                    .where(USER_COMMENT_LIKES.COMMENT_ID.eq(cursor.toString()))
                    .and(USER_COMMENT_LIKES.DELETED_AT.isNull)
            ).map { record -> record.value1() }
                .defaultIfEmpty(0)
                .flatMap { cursorLikeCount ->
                    countRepliesByParentCommentId(cursor)
                        .map { cursorReplyCount ->
                            val cursorPopularityScore = cursorLikeCount + cursorReplyCount
                            // (인기 점수 < 커서 점수) OR (인기 점수 = 커서 점수 AND 생성일 > 커서 생성일) OR
                            // (인기 점수 = 커서 점수 AND 생성일 = 커서 생성일 AND ID > 커서 ID)
                            // 서브쿼리를 사용하여 Instant 타입 변환 이슈 방지
                            popularityScore.lt(cursorPopularityScore)
                                .or(
                                    popularityScore.eq(cursorPopularityScore)
                                        .and(COMMENTS.CREATED_AT.gt(cursorCreatedAtSubquery))
                                )
                                .or(
                                    popularityScore.eq(cursorPopularityScore)
                                        .and(COMMENTS.CREATED_AT.eq(cursorCreatedAtSubquery))
                                        .and(COMMENTS.ID.gt(cursor.toString()))
                                )
                        }
                }
                .flatMapMany { havingCondition ->
                    Flux.from(
                        dslContext
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
                            .leftJoin(USER_COMMENT_LIKES)
                                .on(COMMENTS.ID.eq(USER_COMMENT_LIKES.COMMENT_ID))
                                .and(USER_COMMENT_LIKES.DELETED_AT.isNull)
                            .leftJoin(replies)
                                .on(COMMENTS.ID.eq(replies.PARENT_COMMENT_ID))
                                .and(replies.DELETED_AT.isNull)
                            .where(whereConditions)
                            .groupBy(
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
                            .having(havingCondition)
                            .orderBy(popularityScore.desc(), COMMENTS.CREATED_AT.asc(), COMMENTS.ID.asc())
                            .limit(limit + 1) // hasNext 확인을 위해 +1 조회
                    ).map { record -> recordToComment(record) }
                }
                .switchIfEmpty(
                    // 커서 댓글을 찾을 수 없으면 빈 결과 반환
                    Flux.empty()
                )
        } else {
            // 커서가 없으면 처음부터 조회
            Flux.from(
                dslContext
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
                    .leftJoin(USER_COMMENT_LIKES)
                        .on(COMMENTS.ID.eq(USER_COMMENT_LIKES.COMMENT_ID))
                        .and(USER_COMMENT_LIKES.DELETED_AT.isNull)
                    .leftJoin(replies)
                        .on(COMMENTS.ID.eq(replies.PARENT_COMMENT_ID))
                        .and(replies.DELETED_AT.isNull)
                    .where(whereConditions)
                    .groupBy(
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
                    .orderBy(popularityScore.desc(), COMMENTS.CREATED_AT.asc(), COMMENTS.ID.asc())
                    .limit(limit + 1) // hasNext 확인을 위해 +1 조회
            ).map { record -> recordToComment(record) }
        }
    }

    override fun findRepliesByParentCommentId(parentCommentId: UUID, cursor: UUID?, limit: Int): Flux<Comment> {
        return if (cursor != null) {
            // Cursor의 createdAt을 서브쿼리로 가져옴 (Instant 타입 변환 이슈 방지)
            val cursorCreatedAtSubquery = DSL.select(COMMENTS.CREATED_AT)
                .from(COMMENTS)
                .where(COMMENTS.ID.eq(cursor.toString()))

            Flux.from(
                dslContext
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
                    // 같은 시간에 생성된 댓글도 ID로 구분 (안정적인 cursor 페이징)
                    // 서브쿼리를 사용하여 타입 변환 이슈 방지
                    .and(
                        COMMENTS.CREATED_AT.gt(cursorCreatedAtSubquery)
                            .or(
                                COMMENTS.CREATED_AT.eq(cursorCreatedAtSubquery)
                                    .and(COMMENTS.ID.gt(cursor.toString()))
                            )
                    )
                    .orderBy(COMMENTS.CREATED_AT.asc(), COMMENTS.ID.asc())
                    .limit(limit + 1) // hasNext 확인을 위해 +1 조회
            ).map { record -> recordToComment(record) }
        } else {
            // Cursor가 없으면 처음부터 조회
            Flux.from(
                dslContext
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
                    .orderBy(COMMENTS.CREATED_AT.asc(), COMMENTS.ID.asc())
                    .limit(limit + 1) // hasNext 확인을 위해 +1 조회
            ).map { record -> recordToComment(record) }
        }
    }

    override fun countRepliesByParentCommentId(parentCommentId: UUID): Mono<Int> {
        return Mono.from(
            dslContext
                .selectCount()
                .from(COMMENTS)
                .where(COMMENTS.PARENT_COMMENT_ID.eq(parentCommentId.toString()))
                .and(COMMENTS.DELETED_AT.isNull)
        ).map { record -> record.value1() }
            .defaultIfEmpty(0)
    }

    override fun countRepliesByParentCommentIds(parentCommentIds: List<UUID>): Mono<Map<UUID, Int>> {
        if (parentCommentIds.isEmpty()) {
            return Mono.just(emptyMap())
        }

        val parentCommentIdStrings = parentCommentIds.map { it.toString() }

        return Flux.from(
            dslContext
                .select(
                    COMMENTS.PARENT_COMMENT_ID,
                    DSL.count()
                )
                .from(COMMENTS)
                .where(COMMENTS.PARENT_COMMENT_ID.`in`(parentCommentIdStrings))
                .and(COMMENTS.DELETED_AT.isNull)
                .groupBy(COMMENTS.PARENT_COMMENT_ID)
        ).collectMap(
            { record -> UUID.fromString(record.value1()) },
            { record -> record.value2() }
        )
    }

    override fun delete(commentId: UUID, userId: UUID): Mono<Void> {
        val now = Instant.now()

        return Mono.from(
            dslContext
                .update(COMMENTS)
                .set(COMMENTS.DELETED_AT, now)
                .set(COMMENTS.UPDATED_AT, now)
                .set(COMMENTS.UPDATED_BY, userId.toString())
                .where(COMMENTS.ID.eq(commentId.toString()))
                .and(COMMENTS.DELETED_AT.isNull)
        ).then()
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
            createdBy = record.getValue(COMMENTS.CREATED_BY),
            updatedAt = record.getValue(COMMENTS.UPDATED_AT)!!,
            updatedBy = record.getValue(COMMENTS.UPDATED_BY),
            deletedAt = record.getValue(COMMENTS.DELETED_AT)
        )
    }
}
