package me.onetwo.growsnap.domain.interaction.repository

import me.onetwo.growsnap.domain.interaction.model.UserLike
import me.onetwo.growsnap.jooq.generated.tables.UserLikes.Companion.USER_LIKES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * 사용자 좋아요 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class UserLikeRepositoryImpl(
    private val dslContext: DSLContext
) : UserLikeRepository {

    /**
     * 좋아요 생성
     *
     * ### 처리 흐름
     * 1. user_likes 테이블에 INSERT
     * 2. created_at, created_by, updated_at, updated_by, deleted_at_unix(0) 자동 설정
     *
     * ### 비즈니스 규칙
     * - UNIQUE 제약조건 (user_id, content_id, deleted_at_unix)으로 중복 방지
     * - deleted_at_unix = 0: 활성 상태
     * - deleted_at_unix = Unix timestamp: 삭제된 상태
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 생성된 좋아요 (Mono)
     */
    override fun save(userId: UUID, contentId: UUID): Mono<UserLike> {
        val now = LocalDateTime.now()
        val userIdStr = userId.toString()
        val contentIdStr = contentId.toString()

        // JOOQ의 type-safe API로 INSERT 쿼리 생성
        return Mono.from(
            dslContext
                .insertInto(USER_LIKES)
                .set(USER_LIKES.USER_ID, userIdStr)
                .set(USER_LIKES.CONTENT_ID, contentIdStr)
                .set(USER_LIKES.CREATED_AT, now)
                .set(USER_LIKES.CREATED_BY, userIdStr)
                .set(USER_LIKES.UPDATED_AT, now)
                .set(USER_LIKES.UPDATED_BY, userIdStr)
                .set(USER_LIKES.DELETED_AT_UNIX, 0L)
                .returningResult(
                    USER_LIKES.ID,
                    USER_LIKES.USER_ID,
                    USER_LIKES.CONTENT_ID,
                    USER_LIKES.CREATED_AT,
                    USER_LIKES.CREATED_BY,
                    USER_LIKES.UPDATED_AT,
                    USER_LIKES.UPDATED_BY,
                    USER_LIKES.DELETED_AT
                )
        ).map { record ->
            UserLike(
                id = record.getValue(USER_LIKES.ID),
                userId = UUID.fromString(record.getValue(USER_LIKES.USER_ID)),
                contentId = UUID.fromString(record.getValue(USER_LIKES.CONTENT_ID)),
                createdAt = record.getValue(USER_LIKES.CREATED_AT)!!,
                createdBy = record.getValue(USER_LIKES.CREATED_BY),
                updatedAt = record.getValue(USER_LIKES.UPDATED_AT)!!,
                updatedBy = record.getValue(USER_LIKES.UPDATED_BY),
                deletedAt = record.getValue(USER_LIKES.DELETED_AT)
            )
        }
    }

    /**
     * 좋아요 삭제 (Soft Delete)
     *
     * ### 처리 흐름
     * 1. user_likes 테이블에서 deleted_at, deleted_at_unix 업데이트
     * 2. updated_at, updated_by 갱신
     *
     * ### 비즈니스 규칙
     * - 물리적 삭제 금지, 논리적 삭제만 허용
     * - deleted_at_unix를 현재 Unix timestamp로 설정 (유니크 제약조건 우회)
     * - 이미 삭제된 데이터는 제외 (deleted_at_unix = 0 조건)
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    override fun delete(userId: UUID, contentId: UUID): Mono<Void> {
        val now = LocalDateTime.now()
        val nowUnix = now.toEpochSecond(ZoneOffset.UTC)

        // JOOQ의 type-safe API로 UPDATE 쿼리 생성
        return Mono.from(
            dslContext
                .update(USER_LIKES)
                .set(USER_LIKES.DELETED_AT, now)
                .set(USER_LIKES.DELETED_AT_UNIX, nowUnix)
                .set(USER_LIKES.UPDATED_AT, now)
                .set(USER_LIKES.UPDATED_BY, userId.toString())
                .where(USER_LIKES.USER_ID.eq(userId.toString()))
                .and(USER_LIKES.CONTENT_ID.eq(contentId.toString()))
                .and(USER_LIKES.DELETED_AT_UNIX.eq(0L))
        ).then()
    }

    /**
     * 좋아요 존재 여부 확인
     *
     * deleted_at_unix = 0인 레코드만 확인합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 여부 (Mono<Boolean>)
     */
    override fun exists(userId: UUID, contentId: UUID): Mono<Boolean> {
        // JOOQ의 type-safe API로 SELECT COUNT 쿼리 생성
        return Mono.from(
            dslContext
                .selectCount()
                .from(USER_LIKES)
                .where(USER_LIKES.USER_ID.eq(userId.toString()))
                .and(USER_LIKES.CONTENT_ID.eq(contentId.toString()))
                .and(USER_LIKES.DELETED_AT_UNIX.eq(0L))
        ).map { record -> record.value1() > 0 }
            .defaultIfEmpty(false)
    }

    /**
     * 사용자의 좋아요 조회
     *
     * deleted_at_unix = 0인 레코드만 조회합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 (없으면 empty Mono)
     */
    override fun findByUserIdAndContentId(userId: UUID, contentId: UUID): Mono<UserLike> {
        // JOOQ의 type-safe API로 SELECT 쿼리 생성
        return Mono.from(
            dslContext
                .select(
                    USER_LIKES.ID,
                    USER_LIKES.USER_ID,
                    USER_LIKES.CONTENT_ID,
                    USER_LIKES.CREATED_AT,
                    USER_LIKES.CREATED_BY,
                    USER_LIKES.UPDATED_AT,
                    USER_LIKES.UPDATED_BY,
                    USER_LIKES.DELETED_AT
                )
                .from(USER_LIKES)
                .where(USER_LIKES.USER_ID.eq(userId.toString()))
                .and(USER_LIKES.CONTENT_ID.eq(contentId.toString()))
                .and(USER_LIKES.DELETED_AT_UNIX.eq(0L))
        ).map { record ->
            UserLike(
                id = record.getValue(USER_LIKES.ID),
                userId = UUID.fromString(record.getValue(USER_LIKES.USER_ID)),
                contentId = UUID.fromString(record.getValue(USER_LIKES.CONTENT_ID)),
                createdAt = record.getValue(USER_LIKES.CREATED_AT)!!,
                createdBy = record.getValue(USER_LIKES.CREATED_BY),
                updatedAt = record.getValue(USER_LIKES.UPDATED_AT)!!,
                updatedBy = record.getValue(USER_LIKES.UPDATED_BY),
                deletedAt = record.getValue(USER_LIKES.DELETED_AT)
            )
        }
    }
}
