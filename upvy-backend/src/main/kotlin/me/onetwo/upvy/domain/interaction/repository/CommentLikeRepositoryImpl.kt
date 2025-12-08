package me.onetwo.upvy.domain.interaction.repository

import me.onetwo.upvy.domain.interaction.model.CommentLike
import me.onetwo.upvy.jooq.generated.tables.UserCommentLikes.Companion.USER_COMMENT_LIKES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 댓글 좋아요 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
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
     * 2. created_at, created_by, updated_at, updated_by, deleted_at_unix(0) 자동 설정
     *
     * ### 비즈니스 규칙
     * - UNIQUE 제약조건 (user_id, comment_id, deleted_at_unix)으로 중복 방지
     * - deleted_at_unix = 0: 활성 상태
     * - deleted_at_unix = Unix timestamp: 삭제된 상태
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 생성된 댓글 좋아요 (Mono)
     */
    override fun save(userId: UUID, commentId: UUID): Mono<CommentLike> {
        val now = Instant.now()
        val userIdStr = userId.toString()
        val commentIdStr = commentId.toString()

        // JOOQ의 type-safe API로 INSERT 쿼리 생성
        // MySQL R2DBC는 단일 자동 생성 값만 지원하므로, ID만 반환하고 나머지는 메모리에서 구성
        return Mono.from(
            dslContext
                .insertInto(USER_COMMENT_LIKES)
                .set(USER_COMMENT_LIKES.USER_ID, userIdStr)
                .set(USER_COMMENT_LIKES.COMMENT_ID, commentIdStr)
                .set(USER_COMMENT_LIKES.CREATED_AT, now)
                .set(USER_COMMENT_LIKES.CREATED_BY, userIdStr)
                .set(USER_COMMENT_LIKES.UPDATED_AT, now)
                .set(USER_COMMENT_LIKES.UPDATED_BY, userIdStr)
                .set(USER_COMMENT_LIKES.DELETED_AT_UNIX, 0L)
                .returningResult(USER_COMMENT_LIKES.ID)
        ).map { record ->
            CommentLike(
                id = record.getValue(USER_COMMENT_LIKES.ID),
                userId = userId,
                commentId = commentId,
                createdAt = now,
                createdBy = userIdStr,
                updatedAt = now,
                updatedBy = userIdStr,
                deletedAt = null
            )
        }
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
    override fun delete(userId: UUID, commentId: UUID): Mono<Void> {
        val now = Instant.now()
        val unixTimestamp = now.epochSecond

        // JOOQ의 type-safe API로 UPDATE 쿼리 생성
        return Mono.from(
            dslContext
                .update(USER_COMMENT_LIKES)
                .set(USER_COMMENT_LIKES.DELETED_AT, now)
                .set(USER_COMMENT_LIKES.DELETED_AT_UNIX, unixTimestamp)
                .set(USER_COMMENT_LIKES.UPDATED_AT, now)
                .set(USER_COMMENT_LIKES.UPDATED_BY, userId.toString())
                .where(USER_COMMENT_LIKES.USER_ID.eq(userId.toString()))
                .and(USER_COMMENT_LIKES.COMMENT_ID.eq(commentId.toString()))
                .and(USER_COMMENT_LIKES.DELETED_AT_UNIX.eq(0L))
        ).then()
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
    override fun exists(userId: UUID, commentId: UUID): Mono<Boolean> {
        // JOOQ의 type-safe API로 SELECT COUNT 쿼리 생성
        return Mono.from(
            dslContext
                .selectCount()
                .from(USER_COMMENT_LIKES)
                .where(USER_COMMENT_LIKES.USER_ID.eq(userId.toString()))
                .and(USER_COMMENT_LIKES.COMMENT_ID.eq(commentId.toString()))
                .and(USER_COMMENT_LIKES.DELETED_AT_UNIX.eq(0L))
        ).map { record -> record.value1() > 0 }
            .defaultIfEmpty(false)
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
    override fun findByUserIdAndCommentId(userId: UUID, commentId: UUID): Mono<CommentLike> {
        // JOOQ의 type-safe API로 SELECT 쿼리 생성
        return Mono.from(
            dslContext
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
                .and(USER_COMMENT_LIKES.DELETED_AT_UNIX.eq(0L))
        ).map { record -> toCommentLike(record) }
    }

    /**
     * 댓글의 좋아요 수 조회
     *
     * deleted_at이 NULL인 레코드만 카운트합니다.
     *
     * @param commentId 댓글 ID
     * @return 좋아요 수
     */
    override fun countByCommentId(commentId: UUID): Mono<Int> {
        // JOOQ의 type-safe API로 SELECT COUNT 쿼리 생성
        return Mono.from(
            dslContext
                .selectCount()
                .from(USER_COMMENT_LIKES)
                .where(USER_COMMENT_LIKES.COMMENT_ID.eq(commentId.toString()))
                .and(USER_COMMENT_LIKES.DELETED_AT_UNIX.eq(0L))
        ).map { record -> record.value1() }
            .defaultIfEmpty(0)
    }

    /**
     * 여러 댓글의 좋아요 수 일괄 조회 (N+1 문제 해결)
     *
     * deleted_at이 NULL인 레코드만 카운트합니다.
     *
     * @param commentIds 댓글 ID 목록
     * @return 댓글 ID를 키로, 좋아요 수를 값으로 하는 Map
     */
    override fun countByCommentIds(commentIds: List<UUID>): Mono<Map<UUID, Int>> {
        if (commentIds.isEmpty()) {
            return Mono.just(emptyMap())
        }

        val commentIdStrings = commentIds.map { it.toString() }

        // JOOQ의 type-safe API로 GROUP BY 쿼리 생성 (Flux로 처리)
        return Flux.from(
            dslContext
                .select(
                    USER_COMMENT_LIKES.COMMENT_ID,
                    org.jooq.impl.DSL.count()
                )
                .from(USER_COMMENT_LIKES)
                .where(USER_COMMENT_LIKES.COMMENT_ID.`in`(commentIdStrings))
                .and(USER_COMMENT_LIKES.DELETED_AT_UNIX.eq(0L))
                .groupBy(USER_COMMENT_LIKES.COMMENT_ID)
        ).collectMap(
            { record -> UUID.fromString(record.value1()) },
            { record -> record.value2() }
        ).defaultIfEmpty(emptyMap())
    }

    /**
     * 사용자가 좋아요한 댓글 ID 목록 조회 (N+1 문제 해결)
     *
     * deleted_at이 NULL인 레코드만 조회합니다.
     *
     * @param userId 사용자 ID
     * @param commentIds 댓글 ID 목록
     * @return 사용자가 좋아요한 댓글 ID 집합
     */
    override fun findLikedCommentIds(userId: UUID, commentIds: List<UUID>): Mono<Set<UUID>> {
        if (commentIds.isEmpty()) {
            return Mono.just(emptySet())
        }

        val commentIdStrings = commentIds.map { it.toString() }

        // JOOQ의 type-safe API로 SELECT 쿼리 생성
        return Flux.from(
            dslContext
                .select(USER_COMMENT_LIKES.COMMENT_ID)
                .from(USER_COMMENT_LIKES)
                .where(USER_COMMENT_LIKES.USER_ID.eq(userId.toString()))
                .and(USER_COMMENT_LIKES.COMMENT_ID.`in`(commentIdStrings))
                .and(USER_COMMENT_LIKES.DELETED_AT_UNIX.eq(0L))
        ).map { record -> UUID.fromString(record.value1()) }
            .collect({ mutableSetOf<UUID>() }, { set, id -> set.add(id) })
            .map { it.toSet() }
            .defaultIfEmpty(emptySet())
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
            createdBy = record.getValue(USER_COMMENT_LIKES.CREATED_BY),
            updatedAt = record.getValue(USER_COMMENT_LIKES.UPDATED_AT)!!,
            updatedBy = record.getValue(USER_COMMENT_LIKES.UPDATED_BY),
            deletedAt = record.getValue(USER_COMMENT_LIKES.DELETED_AT)
        )
    }
}
