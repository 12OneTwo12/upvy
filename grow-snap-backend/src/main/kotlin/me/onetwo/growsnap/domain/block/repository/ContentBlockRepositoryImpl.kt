package me.onetwo.growsnap.domain.block.repository

import me.onetwo.growsnap.domain.block.dto.BlockedContentItemResponse
import me.onetwo.growsnap.domain.block.model.ContentBlock
import me.onetwo.growsnap.jooq.generated.tables.ContentBlocks.Companion.CONTENT_BLOCKS
import me.onetwo.growsnap.jooq.generated.tables.ContentMetadata.Companion.CONTENT_METADATA
import me.onetwo.growsnap.jooq.generated.tables.Contents.Companion.CONTENTS
import me.onetwo.growsnap.jooq.generated.tables.UserProfiles.Companion.USER_PROFILES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 콘텐츠 차단 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class ContentBlockRepositoryImpl(
    private val dslContext: DSLContext
) : ContentBlockRepository {

    /**
     * 콘텐츠 차단 생성
     *
     * ### 처리 흐름
     * 1. content_blocks 테이블에 INSERT
     * 2. created_at, created_by, updated_at, updated_by 자동 설정
     *
     * ### 비즈니스 규칙
     * - 중복 차단 방지는 Service 계층에서 처리 (exists 체크)
     * - Soft Delete 지원 (deleted_at)
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 생성된 콘텐츠 차단 (Mono)
     */
    override fun save(
        userId: UUID,
        contentId: UUID
    ): Mono<ContentBlock> {
        val now = Instant.now()
        val userIdStr = userId.toString()
        val contentIdStr = contentId.toString()

        return Mono.from(
            dslContext
                .insertInto(CONTENT_BLOCKS)
                .set(CONTENT_BLOCKS.USER_ID, userIdStr)
                .set(CONTENT_BLOCKS.CONTENT_ID, contentIdStr)
                .set(CONTENT_BLOCKS.CREATED_AT, now)
                .set(CONTENT_BLOCKS.CREATED_BY, userIdStr)
                .set(CONTENT_BLOCKS.UPDATED_AT, now)
                .set(CONTENT_BLOCKS.UPDATED_BY, userIdStr)
                .returningResult(CONTENT_BLOCKS.ID)
        ).map { record ->
            ContentBlock(
                id = record.getValue(CONTENT_BLOCKS.ID),
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
     * 콘텐츠 차단 존재 여부 확인
     *
     * ### 처리 흐름
     * 1. content_blocks 테이블에서 user_id, content_id로 검색
     * 2. deleted_at IS NULL 조건으로 삭제되지 않은 차단만 확인
     * 3. COUNT > 0이면 true, 아니면 false 반환
     *
     * ### 비즈니스 규칙
     * - 동일한 사용자가 동일한 콘텐츠를 중복 차단할 수 없음
     * - 삭제된 차단은 제외 (deleted_at IS NULL)
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 차단 여부 (Mono<Boolean>)
     */
    override fun exists(
        userId: UUID,
        contentId: UUID
    ): Mono<Boolean> {
        return Mono.from(
            dslContext
                .selectCount()
                .from(CONTENT_BLOCKS)
                .where(CONTENT_BLOCKS.USER_ID.eq(userId.toString()))
                .and(CONTENT_BLOCKS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_BLOCKS.DELETED_AT.isNull)
        ).map { record -> record.value1() > 0 }
            .defaultIfEmpty(false)
    }

    /**
     * 콘텐츠 차단 조회
     *
     * ### 처리 흐름
     * 1. content_blocks 테이블에서 user_id, content_id로 검색
     * 2. deleted_at IS NULL 조건으로 삭제되지 않은 차단만 조회
     * 3. 결과를 ContentBlock 모델로 매핑
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 콘텐츠 차단 (없으면 empty Mono)
     */
    override fun findByUserIdAndContentId(
        userId: UUID,
        contentId: UUID
    ): Mono<ContentBlock> {
        return Mono.from(
            dslContext
                .select(
                    CONTENT_BLOCKS.ID,
                    CONTENT_BLOCKS.USER_ID,
                    CONTENT_BLOCKS.CONTENT_ID,
                    CONTENT_BLOCKS.CREATED_AT,
                    CONTENT_BLOCKS.CREATED_BY,
                    CONTENT_BLOCKS.UPDATED_AT,
                    CONTENT_BLOCKS.UPDATED_BY,
                    CONTENT_BLOCKS.DELETED_AT
                )
                .from(CONTENT_BLOCKS)
                .where(CONTENT_BLOCKS.USER_ID.eq(userId.toString()))
                .and(CONTENT_BLOCKS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_BLOCKS.DELETED_AT.isNull)
        ).map { record ->
            ContentBlock(
                id = record.getValue(CONTENT_BLOCKS.ID),
                userId = UUID.fromString(record.getValue(CONTENT_BLOCKS.USER_ID)),
                contentId = UUID.fromString(record.getValue(CONTENT_BLOCKS.CONTENT_ID)),
                createdAt = record.getValue(CONTENT_BLOCKS.CREATED_AT),
                createdBy = record.getValue(CONTENT_BLOCKS.CREATED_BY),
                updatedAt = record.getValue(CONTENT_BLOCKS.UPDATED_AT),
                updatedBy = record.getValue(CONTENT_BLOCKS.UPDATED_BY),
                deletedAt = record.getValue(CONTENT_BLOCKS.DELETED_AT)
            )
        }
    }

    /**
     * 콘텐츠 차단 삭제 (Soft Delete)
     *
     * ### 처리 흐름
     * 1. content_blocks 테이블에서 user_id, content_id로 검색
     * 2. deleted_at, updated_at, updated_by 업데이트
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 삭제 성공 여부 (Mono)
     */
    override fun delete(
        userId: UUID,
        contentId: UUID
    ): Mono<Void> {
        val now = Instant.now()
        val userIdStr = userId.toString()

        return Mono.from(
            dslContext
                .update(CONTENT_BLOCKS)
                .set(CONTENT_BLOCKS.DELETED_AT, now)
                .set(CONTENT_BLOCKS.UPDATED_AT, now)
                .set(CONTENT_BLOCKS.UPDATED_BY, userIdStr)
                .where(CONTENT_BLOCKS.USER_ID.eq(userIdStr))
                .and(CONTENT_BLOCKS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_BLOCKS.DELETED_AT.isNull)
        ).then()
    }

    /**
     * 차단한 콘텐츠 목록 조회 (커서 기반 페이지네이션)
     *
     * ### 처리 흐름
     * 1. content_blocks 테이블을 contents, content_metadata, user_profiles와 LEFT JOIN
     * 2. user_id로 필터링
     * 3. deleted_at IS NULL 조건으로 삭제되지 않은 차단만 조회
     * 4. 커서 기반 페이지네이션 적용 (block_id)
     * 5. 결과를 BlockedContentItemResponse로 매핑
     *
     * @param userId 차단한 사용자 ID
     * @param cursor 커서 (차단 ID)
     * @param limit 조회 개수
     * @return 차단한 콘텐츠 목록 (Flux)
     */
    override fun findBlockedContentsByUserId(
        userId: UUID,
        cursor: Long?,
        limit: Int
    ): Flux<BlockedContentItemResponse> {
        val contentIdField = CONTENTS.ID.`as`("content_id")

        var query = dslContext
            .select(
                CONTENT_BLOCKS.ID,
                contentIdField,
                CONTENT_METADATA.TITLE,
                CONTENTS.THUMBNAIL_URL,
                USER_PROFILES.NICKNAME,
                CONTENT_BLOCKS.CREATED_AT
            )
            .from(CONTENT_BLOCKS)
            .leftJoin(CONTENTS).on(CONTENT_BLOCKS.CONTENT_ID.eq(CONTENTS.ID))
            .leftJoin(CONTENT_METADATA).on(CONTENTS.ID.eq(CONTENT_METADATA.CONTENT_ID))
            .leftJoin(USER_PROFILES).on(CONTENTS.CREATOR_ID.eq(USER_PROFILES.USER_ID))
            .where(CONTENT_BLOCKS.USER_ID.eq(userId.toString()))
            .and(CONTENT_BLOCKS.DELETED_AT.isNull)

        if (cursor != null) {
            query = query.and(CONTENT_BLOCKS.ID.lt(cursor))
        }

        return Flux.from(
            query
                .orderBy(CONTENT_BLOCKS.ID.desc())
                .limit(limit)
        ).map { record ->
            BlockedContentItemResponse(
                blockId = record.getValue(CONTENT_BLOCKS.ID)!!,
                contentId = record.getValue(contentIdField)!!,
                title = record.getValue(CONTENT_METADATA.TITLE) ?: "Unknown",
                thumbnailUrl = record.getValue(CONTENTS.THUMBNAIL_URL) ?: "",
                creatorNickname = record.getValue(USER_PROFILES.NICKNAME) ?: "Unknown",
                blockedAt = record.getValue(CONTENT_BLOCKS.CREATED_AT)!!
            )
        }
    }
}
