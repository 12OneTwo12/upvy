package me.onetwo.growsnap.domain.interaction.repository

import me.onetwo.growsnap.domain.interaction.model.UserSave
import me.onetwo.growsnap.jooq.generated.tables.UserSaves.Companion.USER_SAVES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * 사용자 저장 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class UserSaveRepositoryImpl(
    private val dslContext: DSLContext
) : UserSaveRepository {

    /**
     * 콘텐츠 저장
     *
     * ### 처리 흐름
     * 1. user_saves 테이블에 INSERT
     * 2. created_at, created_by, updated_at, updated_by, deleted_at_unix(0) 자동 설정
     *
     * ### 비즈니스 규칙
     * - UNIQUE 제약조건 (user_id, content_id, deleted_at_unix)으로 중복 방지
     * - deleted_at_unix = 0: 활성 상태
     * - deleted_at_unix = Unix timestamp: 삭제된 상태
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 생성된 저장 (Mono)
     */
    override fun save(userId: UUID, contentId: UUID): Mono<UserSave> {
        val now = Instant.now()
        val userIdStr = userId.toString()
        val contentIdStr = contentId.toString()

        // JOOQ의 type-safe API로 INSERT 쿼리 생성
        // MySQL R2DBC는 단일 자동 생성 값만 지원하므로, ID만 반환하고 나머지는 메모리에서 구성
        return Mono.from(
            dslContext
                .insertInto(USER_SAVES)
                .set(USER_SAVES.USER_ID, userIdStr)
                .set(USER_SAVES.CONTENT_ID, contentIdStr)
                .set(USER_SAVES.CREATED_AT, now)
                .set(USER_SAVES.CREATED_BY, userIdStr)
                .set(USER_SAVES.UPDATED_AT, now)
                .set(USER_SAVES.UPDATED_BY, userIdStr)
                .set(USER_SAVES.DELETED_AT_UNIX, 0L)
                .returningResult(USER_SAVES.ID)
        ).map { record ->
            UserSave(
                id = record.getValue(USER_SAVES.ID),
                userId = userId,
                contentId = contentId,
                createdAt = now,
                createdBy = userIdStr,
                updatedAt = now,
                updatedBy = userIdStr,
                deletedAt = null
            )
        }
    }

    /**
     * 콘텐츠 저장 취소 (Soft Delete)
     *
     * ### 처리 흐름
     * 1. user_saves 테이블에서 deleted_at, deleted_at_unix 업데이트
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
        val now = Instant.now()
        val nowUnix = now.getEpochSecond()

        // JOOQ의 type-safe API로 UPDATE 쿼리 생성
        return Mono.from(
            dslContext
                .update(USER_SAVES)
                .set(USER_SAVES.DELETED_AT, now)
                .set(USER_SAVES.DELETED_AT_UNIX, nowUnix)
                .set(USER_SAVES.UPDATED_AT, now)
                .set(USER_SAVES.UPDATED_BY, userId.toString())
                .where(USER_SAVES.USER_ID.eq(userId.toString()))
                .and(USER_SAVES.CONTENT_ID.eq(contentId.toString()))
                .and(USER_SAVES.DELETED_AT_UNIX.eq(0L))
        ).then()
    }

    /**
     * 저장 여부 확인
     *
     * deleted_at_unix = 0인 레코드만 확인합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 저장 여부 (Mono<Boolean>)
     */
    override fun exists(userId: UUID, contentId: UUID): Mono<Boolean> {
        // JOOQ의 type-safe API로 SELECT COUNT 쿼리 생성
        return Mono.from(
            dslContext
                .selectCount()
                .from(USER_SAVES)
                .where(USER_SAVES.USER_ID.eq(userId.toString()))
                .and(USER_SAVES.CONTENT_ID.eq(contentId.toString()))
                .and(USER_SAVES.DELETED_AT_UNIX.eq(0L))
        ).map { record -> record.value1() > 0 }
            .defaultIfEmpty(false)
    }

    /**
     * 사용자의 저장 목록 조회
     *
     * deleted_at_unix = 0인 레코드만 조회하며, 생성일 기준 내림차순 정렬합니다.
     *
     * @param userId 사용자 ID
     * @return 저장 목록 (Flux)
     */
    override fun findByUserId(userId: UUID): Flux<UserSave> {
        // JOOQ의 type-safe API로 SELECT 쿼리 생성
        return Flux.from(
            dslContext
                .select(
                    USER_SAVES.ID,
                    USER_SAVES.USER_ID,
                    USER_SAVES.CONTENT_ID,
                    USER_SAVES.CREATED_AT,
                    USER_SAVES.CREATED_BY,
                    USER_SAVES.UPDATED_AT,
                    USER_SAVES.UPDATED_BY,
                    USER_SAVES.DELETED_AT
                )
                .from(USER_SAVES)
                .where(USER_SAVES.USER_ID.eq(userId.toString()))
                .and(USER_SAVES.DELETED_AT_UNIX.eq(0L))
                .orderBy(USER_SAVES.CREATED_AT.desc())
        ).map { record ->
            UserSave(
                id = record.getValue(USER_SAVES.ID),
                userId = UUID.fromString(record.getValue(USER_SAVES.USER_ID)),
                contentId = UUID.fromString(record.getValue(USER_SAVES.CONTENT_ID)),
                createdAt = record.getValue(USER_SAVES.CREATED_AT)!!,
                createdBy = record.getValue(USER_SAVES.CREATED_BY),
                updatedAt = record.getValue(USER_SAVES.UPDATED_AT)!!,
                updatedBy = record.getValue(USER_SAVES.UPDATED_BY),
                deletedAt = record.getValue(USER_SAVES.DELETED_AT)
            )
        }
    }

    /**
     * 사용자의 저장 목록을 커서 기반 페이징으로 조회
     *
     * deleted_at_unix = 0인 레코드만 조회하며, 생성일 기준 내림차순 정렬합니다.
     * ID 기반 커서 페이지네이션을 사용합니다.
     *
     * @param userId 사용자 ID
     * @param cursor 이전 페이지의 마지막 저장 ID (null이면 첫 페이지)
     * @param limit 페이지당 항목 수
     * @return 저장 목록 (Flux)
     */
    override fun findByUserIdWithCursor(
        userId: UUID,
        cursor: Long?,
        limit: Int
    ): Flux<UserSave> {
        var query = dslContext
            .select(
                USER_SAVES.ID,
                USER_SAVES.USER_ID,
                USER_SAVES.CONTENT_ID,
                USER_SAVES.CREATED_AT,
                USER_SAVES.CREATED_BY,
                USER_SAVES.UPDATED_AT,
                USER_SAVES.UPDATED_BY,
                USER_SAVES.DELETED_AT
            )
            .from(USER_SAVES)
            .where(USER_SAVES.USER_ID.eq(userId.toString()))
            .and(USER_SAVES.DELETED_AT_UNIX.eq(0L))

        // 커서 기반 페이지네이션 (ID 기준)
        if (cursor != null) {
            query = query.and(USER_SAVES.ID.lt(cursor))
        }

        return Flux.from(
            query
                .orderBy(USER_SAVES.ID.desc())
                .limit(limit)
        ).map { record ->
            UserSave(
                id = record.getValue(USER_SAVES.ID),
                userId = UUID.fromString(record.getValue(USER_SAVES.USER_ID)),
                contentId = UUID.fromString(record.getValue(USER_SAVES.CONTENT_ID)),
                createdAt = record.getValue(USER_SAVES.CREATED_AT)!!,
                createdBy = record.getValue(USER_SAVES.CREATED_BY),
                updatedAt = record.getValue(USER_SAVES.UPDATED_AT)!!,
                updatedBy = record.getValue(USER_SAVES.UPDATED_BY),
                deletedAt = record.getValue(USER_SAVES.DELETED_AT)
            )
        }
    }
}
