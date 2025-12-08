package me.onetwo.upvy.domain.block.repository

import me.onetwo.upvy.domain.block.dto.BlockedUserItemResponse
import me.onetwo.upvy.domain.block.model.UserBlock
import me.onetwo.upvy.jooq.generated.tables.UserBlocks.Companion.USER_BLOCKS
import me.onetwo.upvy.jooq.generated.tables.UserProfiles.Companion.USER_PROFILES
import me.onetwo.upvy.jooq.generated.tables.Users.Companion.USERS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 사용자 차단 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class UserBlockRepositoryImpl(
    private val dslContext: DSLContext
) : UserBlockRepository {

    /**
     * 사용자 차단 생성 또는 복구 (UPSERT 패턴)
     *
     * ### 처리 흐름
     * - `INSERT ... ON DUPLICATE KEY UPDATE` 사용
     * - 새 차단: INSERT 실행
     * - 기존 차단 (soft deleted 포함): deleted_at = NULL로 복구
     *
     * ### 성능 최적화
     * - 1번의 DB 쿼리로 처리 (Atomic operation)
     * - Race condition 완전 방지
     * - SELECT 없이 UPSERT 직접 실행
     *
     * ### Soft Delete 지원
     * - UNIQUE 제약 조건: (blocker_id, blocked_id)
     * - 중복 키 발생 시 deleted_at을 NULL로 업데이트하여 차단 복구
     * - 이력 유지를 위해 레코드는 삭제하지 않음
     *
     * @param blockerId 차단한 사용자 ID
     * @param blockedId 차단된 사용자 ID
     * @return 생성 또는 복구된 사용자 차단 (Mono)
     */
    override fun save(
        blockerId: UUID,
        blockedId: UUID
    ): Mono<UserBlock> {
        val now = Instant.now()
        val blockerIdStr = blockerId.toString()
        val blockedIdStr = blockedId.toString()

        return Mono.from(
            dslContext
                .insertInto(USER_BLOCKS)
                .set(USER_BLOCKS.BLOCKER_ID, blockerIdStr)
                .set(USER_BLOCKS.BLOCKED_ID, blockedIdStr)
                .set(USER_BLOCKS.CREATED_AT, now)
                .set(USER_BLOCKS.CREATED_BY, blockerIdStr)
                .set(USER_BLOCKS.UPDATED_AT, now)
                .set(USER_BLOCKS.UPDATED_BY, blockerIdStr)
                .onDuplicateKeyUpdate()
                .set(USER_BLOCKS.DELETED_AT, null as Instant?)
                .set(USER_BLOCKS.UPDATED_AT, now)
                .set(USER_BLOCKS.UPDATED_BY, blockerIdStr)
                .returningResult(USER_BLOCKS.ID)
        ).flatMap { record ->
            val blockId = record.getValue(USER_BLOCKS.ID)

            // UPSERT 후 최신 데이터 조회
            findByBlockerIdAndBlockedId(blockerId, blockedId)
                .map { it.copy(id = blockId) }
        }.switchIfEmpty(
            // returningResult가 실패한 경우 (일부 DB 드라이버 제한)
            Mono.just(
                UserBlock(
                    id = null,
                    blockerId = blockerId,
                    blockedId = blockedId,
                    createdAt = now,
                    createdBy = blockerIdStr,
                    updatedAt = now,
                    updatedBy = blockerIdStr,
                    deletedAt = null
                )
            )
        )
    }

    /**
     * 사용자 차단 존재 여부 확인
     *
     * ### 처리 흐름
     * 1. user_blocks 테이블에서 blocker_id, blocked_id로 검색
     * 2. deleted_at IS NULL 조건으로 삭제되지 않은 차단만 확인
     * 3. COUNT > 0이면 true, 아니면 false 반환
     *
     * ### 비즈니스 규칙
     * - 동일한 사용자가 동일한 사용자를 중복 차단할 수 없음
     * - 삭제된 차단은 제외 (deleted_at IS NULL)
     *
     * @param blockerId 차단한 사용자 ID
     * @param blockedId 차단된 사용자 ID
     * @return 차단 여부 (Mono<Boolean>)
     */
    override fun exists(
        blockerId: UUID,
        blockedId: UUID
    ): Mono<Boolean> {
        return Mono.from(
            dslContext
                .selectCount()
                .from(USER_BLOCKS)
                .where(USER_BLOCKS.BLOCKER_ID.eq(blockerId.toString()))
                .and(USER_BLOCKS.BLOCKED_ID.eq(blockedId.toString()))
                .and(USER_BLOCKS.DELETED_AT.isNull)
        ).map { record -> record.value1() > 0 }
            .defaultIfEmpty(false)
    }

    /**
     * 사용자 차단 조회 (deleted_at 상관없이)
     *
     * ### 처리 흐름
     * 1. user_blocks 테이블에서 blocker_id, blocked_id로 검색
     * 2. deleted_at 상관없이 조회 (UPSERT 패턴 지원)
     * 3. 결과를 UserBlock 모델로 매핑
     *
     * ### 참고
     * - UPSERT 패턴에서 사용되므로 deleted_at 조건 없이 조회
     * - exists 메서드는 deleted_at IS NULL 조건 사용
     *
     * @param blockerId 차단한 사용자 ID
     * @param blockedId 차단된 사용자 ID
     * @return 사용자 차단 (없으면 empty Mono)
     */
    override fun findByBlockerIdAndBlockedId(
        blockerId: UUID,
        blockedId: UUID
    ): Mono<UserBlock> {
        return Mono.from(
            dslContext
                .select(
                    USER_BLOCKS.ID,
                    USER_BLOCKS.BLOCKER_ID,
                    USER_BLOCKS.BLOCKED_ID,
                    USER_BLOCKS.CREATED_AT,
                    USER_BLOCKS.CREATED_BY,
                    USER_BLOCKS.UPDATED_AT,
                    USER_BLOCKS.UPDATED_BY,
                    USER_BLOCKS.DELETED_AT
                )
                .from(USER_BLOCKS)
                .where(USER_BLOCKS.BLOCKER_ID.eq(blockerId.toString()))
                .and(USER_BLOCKS.BLOCKED_ID.eq(blockedId.toString()))
        ).map { record ->
            UserBlock(
                id = record.getValue(USER_BLOCKS.ID),
                blockerId = UUID.fromString(record.getValue(USER_BLOCKS.BLOCKER_ID)),
                blockedId = UUID.fromString(record.getValue(USER_BLOCKS.BLOCKED_ID)),
                createdAt = record.getValue(USER_BLOCKS.CREATED_AT),
                createdBy = record.getValue(USER_BLOCKS.CREATED_BY),
                updatedAt = record.getValue(USER_BLOCKS.UPDATED_AT),
                updatedBy = record.getValue(USER_BLOCKS.UPDATED_BY),
                deletedAt = record.getValue(USER_BLOCKS.DELETED_AT)
            )
        }
    }

    /**
     * 사용자 차단 삭제 (Soft Delete)
     *
     * ### 처리 흐름
     * 1. user_blocks 테이블에서 blocker_id, blocked_id로 검색
     * 2. deleted_at, updated_at, updated_by 업데이트
     *
     * @param blockerId 차단한 사용자 ID
     * @param blockedId 차단된 사용자 ID
     * @return 삭제 성공 여부 (Mono)
     */
    override fun delete(
        blockerId: UUID,
        blockedId: UUID
    ): Mono<Void> {
        val now = Instant.now()
        val blockerIdStr = blockerId.toString()

        return Mono.from(
            dslContext
                .update(USER_BLOCKS)
                .set(USER_BLOCKS.DELETED_AT, now)
                .set(USER_BLOCKS.UPDATED_AT, now)
                .set(USER_BLOCKS.UPDATED_BY, blockerIdStr)
                .where(USER_BLOCKS.BLOCKER_ID.eq(blockerIdStr))
                .and(USER_BLOCKS.BLOCKED_ID.eq(blockedId.toString()))
                .and(USER_BLOCKS.DELETED_AT.isNull)
        ).then()
    }

    /**
     * 차단한 사용자 목록 조회 (커서 기반 페이지네이션)
     *
     * ### 처리 흐름
     * 1. user_blocks 테이블을 users, user_profiles와 LEFT JOIN
     * 2. blocker_id로 필터링
     * 3. deleted_at IS NULL 조건으로 삭제되지 않은 차단만 조회
     * 4. 커서 기반 페이지네이션 적용 (block_id)
     * 5. 결과를 BlockedUserItemResponse로 매핑
     *
     * @param blockerId 차단한 사용자 ID
     * @param cursor 커서 (차단 ID)
     * @param limit 조회 개수
     * @return 차단한 사용자 목록 (Flux)
     */
    override fun findBlockedUsersByBlockerId(
        blockerId: UUID,
        cursor: Long?,
        limit: Int
    ): Flux<BlockedUserItemResponse> {
        val userIdField = USERS.ID.`as`("user_id")

        var query = dslContext
            .select(
                USER_BLOCKS.ID,
                userIdField,
                USER_PROFILES.NICKNAME,
                USER_PROFILES.PROFILE_IMAGE_URL,
                USER_BLOCKS.CREATED_AT
            )
            .from(USER_BLOCKS)
            .leftJoin(USERS).on(USER_BLOCKS.BLOCKED_ID.eq(USERS.ID))
            .leftJoin(USER_PROFILES).on(USERS.ID.eq(USER_PROFILES.USER_ID))
            .where(USER_BLOCKS.BLOCKER_ID.eq(blockerId.toString()))
            .and(USER_BLOCKS.DELETED_AT.isNull)

        if (cursor != null) {
            query = query.and(USER_BLOCKS.ID.lt(cursor))
        }

        return Flux.from(
            query
                .orderBy(USER_BLOCKS.ID.desc())
                .limit(limit)
        ).map { record ->
            BlockedUserItemResponse(
                blockId = record.getValue(USER_BLOCKS.ID)!!,
                userId = record.getValue(userIdField)!!,
                nickname = record.getValue(USER_PROFILES.NICKNAME) ?: "Unknown",
                profileImageUrl = record.getValue(USER_PROFILES.PROFILE_IMAGE_URL),
                blockedAt = record.getValue(USER_BLOCKS.CREATED_AT)!!
            )
        }
    }
}
