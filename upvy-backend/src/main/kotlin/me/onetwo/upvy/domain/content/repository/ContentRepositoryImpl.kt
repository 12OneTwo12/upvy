package me.onetwo.upvy.domain.content.repository

import me.onetwo.upvy.domain.content.dto.ContentWithMetadata
import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.domain.content.model.Content
import me.onetwo.upvy.domain.content.model.ContentMetadata
import me.onetwo.upvy.domain.content.model.ContentStatus
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.jooq.generated.tables.references.CONTENTS
import me.onetwo.upvy.jooq.generated.tables.references.CONTENT_METADATA
import me.onetwo.upvy.jooq.generated.tables.references.CONTENT_TAGS
import me.onetwo.upvy.jooq.generated.tables.references.TAGS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 콘텐츠 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class ContentRepositoryImpl(
    private val dslContext: DSLContext
) : ContentRepository {

    private val logger = LoggerFactory.getLogger(ContentRepositoryImpl::class.java)

    /**
     * 콘텐츠를 저장합니다.
     *
     * @param content 저장할 콘텐츠
     * @return 저장된 콘텐츠 (Mono)
     */
    override fun save(content: Content): Mono<Content> {
        return Mono.from(
            dslContext
                .insertInto(CONTENTS)
                .set(CONTENTS.ID, content.id.toString())
                .set(CONTENTS.CREATOR_ID, content.creatorId.toString())
                .set(CONTENTS.CONTENT_TYPE, content.contentType.name)
                .set(CONTENTS.URL, content.url)
                .set(CONTENTS.THUMBNAIL_URL, content.thumbnailUrl)
                .set(CONTENTS.DURATION, content.duration)
                .set(CONTENTS.WIDTH, content.width)
                .set(CONTENTS.HEIGHT, content.height)
                .set(CONTENTS.STATUS, content.status.name)
                .set(CONTENTS.CREATED_AT, content.createdAt)
                .set(CONTENTS.CREATED_BY, content.createdBy?.toString())
                .set(CONTENTS.UPDATED_AT, content.updatedAt)
                .set(CONTENTS.UPDATED_BY, content.updatedBy?.toString())
                .returningResult(CONTENTS.ID)
        ).map { record ->
            logger.debug("Content saved: contentId=${content.id}")
            content
        }
    }

    /**
     * 콘텐츠를 ID로 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 조회된 콘텐츠 (없으면 empty Mono)
     */
    override fun findById(contentId: UUID): Mono<Content> {
        return Mono.from(
            dslContext
                .select(
                    CONTENTS.ID,
                    CONTENTS.CREATOR_ID,
                    CONTENTS.CONTENT_TYPE,
                    CONTENTS.URL,
                    CONTENTS.THUMBNAIL_URL,
                    CONTENTS.DURATION,
                    CONTENTS.WIDTH,
                    CONTENTS.HEIGHT,
                    CONTENTS.STATUS,
                    CONTENTS.CREATED_AT,
                    CONTENTS.CREATED_BY,
                    CONTENTS.UPDATED_AT,
                    CONTENTS.UPDATED_BY,
                    CONTENTS.DELETED_AT
                )
                .from(CONTENTS)
                .where(CONTENTS.ID.eq(contentId.toString()))
                .and(CONTENTS.DELETED_AT.isNull)
        ).map { record ->
            Content(
                id = UUID.fromString(record.getValue(CONTENTS.ID)),
                creatorId = UUID.fromString(record.getValue(CONTENTS.CREATOR_ID)),
                contentType = ContentType.valueOf(record.getValue(CONTENTS.CONTENT_TYPE)!!),
                url = record.getValue(CONTENTS.URL)!!,
                thumbnailUrl = record.getValue(CONTENTS.THUMBNAIL_URL)!!,
                duration = record.getValue(CONTENTS.DURATION),
                width = record.getValue(CONTENTS.WIDTH)!!,
                height = record.getValue(CONTENTS.HEIGHT)!!,
                status = ContentStatus.valueOf(record.getValue(CONTENTS.STATUS)!!),
                createdAt = record.getValue(CONTENTS.CREATED_AT)!!,
                createdBy = record.getValue(CONTENTS.CREATED_BY),
                updatedAt = record.getValue(CONTENTS.UPDATED_AT)!!,
                updatedBy = record.getValue(CONTENTS.UPDATED_BY),
                deletedAt = record.getValue(CONTENTS.DELETED_AT)
            )
        }
    }

    /**
     * 크리에이터의 콘텐츠 목록을 조회합니다.
     *
     * @param creatorId 크리에이터 ID
     * @return 콘텐츠 목록 (Flux)
     */
    override fun findByCreatorId(creatorId: UUID): Flux<Content> {
        return Flux.from(
            dslContext
                .select(
                    CONTENTS.ID,
                    CONTENTS.CREATOR_ID,
                    CONTENTS.CONTENT_TYPE,
                    CONTENTS.URL,
                    CONTENTS.THUMBNAIL_URL,
                    CONTENTS.DURATION,
                    CONTENTS.WIDTH,
                    CONTENTS.HEIGHT,
                    CONTENTS.STATUS,
                    CONTENTS.CREATED_AT,
                    CONTENTS.CREATED_BY,
                    CONTENTS.UPDATED_AT,
                    CONTENTS.UPDATED_BY,
                    CONTENTS.DELETED_AT
                )
                .from(CONTENTS)
                .where(CONTENTS.CREATOR_ID.eq(creatorId.toString()))
                .and(CONTENTS.DELETED_AT.isNull)
                .orderBy(CONTENTS.CREATED_AT.desc())
        ).map { record ->
            Content(
                id = UUID.fromString(record.getValue(CONTENTS.ID)),
                creatorId = UUID.fromString(record.getValue(CONTENTS.CREATOR_ID)),
                contentType = ContentType.valueOf(record.getValue(CONTENTS.CONTENT_TYPE)!!),
                url = record.getValue(CONTENTS.URL)!!,
                thumbnailUrl = record.getValue(CONTENTS.THUMBNAIL_URL)!!,
                duration = record.getValue(CONTENTS.DURATION),
                width = record.getValue(CONTENTS.WIDTH)!!,
                height = record.getValue(CONTENTS.HEIGHT)!!,
                status = ContentStatus.valueOf(record.getValue(CONTENTS.STATUS)!!),
                createdAt = record.getValue(CONTENTS.CREATED_AT)!!,
                createdBy = record.getValue(CONTENTS.CREATED_BY),
                updatedAt = record.getValue(CONTENTS.UPDATED_AT)!!,
                updatedBy = record.getValue(CONTENTS.UPDATED_BY),
                deletedAt = record.getValue(CONTENTS.DELETED_AT)
            )
        }
    }

    /**
     * 크리에이터의 콘텐츠와 메타데이터를 한 번에 조회합니다.
     *
     * JOIN을 사용하여 N+1 쿼리 문제를 방지합니다.
     *
     * @param creatorId 크리에이터 ID
     * @return 콘텐츠와 메타데이터의 목록 (Flux)
     */
    override fun findWithMetadataByCreatorId(creatorId: UUID): Flux<ContentWithMetadata> {
        val tagsField = DSL.groupConcat(TAGS.NAME).separator(",").`as`("tag_names")

        return Flux.from(
            dslContext
                .select(
                    CONTENTS.ID,
                    CONTENTS.CREATOR_ID,
                    CONTENTS.CONTENT_TYPE,
                    CONTENTS.URL,
                    CONTENTS.THUMBNAIL_URL,
                    CONTENTS.DURATION,
                    CONTENTS.WIDTH,
                    CONTENTS.HEIGHT,
                    CONTENTS.STATUS,
                    CONTENTS.CREATED_AT,
                    CONTENTS.CREATED_BY,
                    CONTENTS.UPDATED_AT,
                    CONTENTS.UPDATED_BY,
                    CONTENTS.DELETED_AT,
                    CONTENT_METADATA.ID,
                    CONTENT_METADATA.CONTENT_ID,
                    CONTENT_METADATA.TITLE,
                    CONTENT_METADATA.DESCRIPTION,
                    CONTENT_METADATA.CATEGORY,
                    CONTENT_METADATA.LANGUAGE,
                    CONTENT_METADATA.CREATED_AT,
                    CONTENT_METADATA.CREATED_BY,
                    CONTENT_METADATA.UPDATED_AT,
                    CONTENT_METADATA.UPDATED_BY,
                    CONTENT_METADATA.DELETED_AT,
                    tagsField
                )
                .from(CONTENTS)
                .innerJoin(CONTENT_METADATA).on(CONTENTS.ID.eq(CONTENT_METADATA.CONTENT_ID))
                .leftJoin(CONTENT_TAGS).on(
                    CONTENTS.ID.eq(CONTENT_TAGS.CONTENT_ID)
                        .and(CONTENT_TAGS.DELETED_AT.isNull)
                )
                .leftJoin(TAGS).on(
                    CONTENT_TAGS.TAG_ID.eq(TAGS.ID)
                        .and(TAGS.DELETED_AT.isNull)
                )
                .where(CONTENTS.CREATOR_ID.eq(creatorId.toString()))
                .and(CONTENTS.DELETED_AT.isNull)
                .and(CONTENT_METADATA.DELETED_AT.isNull)
                .groupBy(
                    CONTENTS.ID,
                    CONTENTS.CREATOR_ID,
                    CONTENTS.CONTENT_TYPE,
                    CONTENTS.URL,
                    CONTENTS.THUMBNAIL_URL,
                    CONTENTS.DURATION,
                    CONTENTS.WIDTH,
                    CONTENTS.HEIGHT,
                    CONTENTS.STATUS,
                    CONTENTS.CREATED_AT,
                    CONTENTS.CREATED_BY,
                    CONTENTS.UPDATED_AT,
                    CONTENTS.UPDATED_BY,
                    CONTENTS.DELETED_AT,
                    CONTENT_METADATA.ID,
                    CONTENT_METADATA.CONTENT_ID,
                    CONTENT_METADATA.TITLE,
                    CONTENT_METADATA.DESCRIPTION,
                    CONTENT_METADATA.CATEGORY,
                    CONTENT_METADATA.LANGUAGE,
                    CONTENT_METADATA.CREATED_AT,
                    CONTENT_METADATA.CREATED_BY,
                    CONTENT_METADATA.UPDATED_AT,
                    CONTENT_METADATA.UPDATED_BY,
                    CONTENT_METADATA.DELETED_AT
                )
                .orderBy(CONTENTS.CREATED_AT.desc())
        ).map { record ->
            val content = Content(
                id = UUID.fromString(record.getValue(CONTENTS.ID)),
                creatorId = UUID.fromString(record.getValue(CONTENTS.CREATOR_ID)),
                contentType = ContentType.valueOf(record.getValue(CONTENTS.CONTENT_TYPE)!!),
                url = record.getValue(CONTENTS.URL)!!,
                thumbnailUrl = record.getValue(CONTENTS.THUMBNAIL_URL)!!,
                duration = record.getValue(CONTENTS.DURATION),
                width = record.getValue(CONTENTS.WIDTH)!!,
                height = record.getValue(CONTENTS.HEIGHT)!!,
                status = ContentStatus.valueOf(record.getValue(CONTENTS.STATUS)!!),
                createdAt = record.getValue(CONTENTS.CREATED_AT)!!,
                createdBy = record.getValue(CONTENTS.CREATED_BY),
                updatedAt = record.getValue(CONTENTS.UPDATED_AT)!!,
                updatedBy = record.getValue(CONTENTS.UPDATED_BY),
                deletedAt = record.getValue(CONTENTS.DELETED_AT)
            )

            // 태그 파싱 - GROUP_CONCAT으로 가져온 tags
            val tagsString = record.get("tag_names", String::class.java)
            val tags = if (!tagsString.isNullOrBlank()) {
                tagsString.split(",").filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val metadata = ContentMetadata(
                id = record.getValue(CONTENT_METADATA.ID),
                contentId = UUID.fromString(record.getValue(CONTENT_METADATA.CONTENT_ID)),
                title = record.getValue(CONTENT_METADATA.TITLE)!!,
                description = record.getValue(CONTENT_METADATA.DESCRIPTION),
                category = Category.valueOf(record.getValue(CONTENT_METADATA.CATEGORY)!!),
                tags = tags,
                language = record.getValue(CONTENT_METADATA.LANGUAGE)!!,
                createdAt = record.getValue(CONTENT_METADATA.CREATED_AT)!!,
                createdBy = record.getValue(CONTENT_METADATA.CREATED_BY),
                updatedAt = record.getValue(CONTENT_METADATA.UPDATED_AT)!!,
                updatedBy = record.getValue(CONTENT_METADATA.UPDATED_BY),
                deletedAt = record.getValue(CONTENT_METADATA.DELETED_AT)
            )

            ContentWithMetadata(content, metadata)
        }
    }

    /**
     * 콘텐츠를 수정합니다.
     *
     * @param content 수정할 콘텐츠
     * @return 수정 성공 여부 (Mono)
     */
    override fun update(content: Content): Mono<Boolean> {
        return Mono.from(
            dslContext
                .update(CONTENTS)
                .set(CONTENTS.URL, content.url)
                .set(CONTENTS.THUMBNAIL_URL, content.thumbnailUrl)
                .set(CONTENTS.DURATION, content.duration)
                .set(CONTENTS.WIDTH, content.width)
                .set(CONTENTS.HEIGHT, content.height)
                .set(CONTENTS.STATUS, content.status.name)
                .set(CONTENTS.UPDATED_AT, content.updatedAt)
                .set(CONTENTS.UPDATED_BY, content.updatedBy?.toString())
                .where(CONTENTS.ID.eq(content.id.toString()))
                .and(CONTENTS.DELETED_AT.isNull)
        ).map { rowsAffected: Any ->
            val count: Boolean = when (rowsAffected) {
                is Long -> rowsAffected > 0L
                is Int -> rowsAffected > 0
                else -> rowsAffected.toString().toLongOrNull()?.let { it > 0L } ?: false
            }
            count
        }
            .defaultIfEmpty(false)
    }

    /**
     * 콘텐츠를 삭제합니다 (Soft Delete).
     *
     * @param contentId 삭제할 콘텐츠 ID
     * @param deletedBy 삭제한 사용자 ID
     * @return 삭제 성공 여부 (Mono)
     */
    override fun delete(contentId: UUID, deletedBy: UUID): Mono<Boolean> {
        return Mono.from(
            dslContext
                .update(CONTENTS)
                .set(CONTENTS.DELETED_AT, Instant.now())
                .set(CONTENTS.UPDATED_AT, Instant.now())
                .set(CONTENTS.UPDATED_BY, deletedBy.toString())
                .where(CONTENTS.ID.eq(contentId.toString()))
                .and(CONTENTS.DELETED_AT.isNull)
        ).map { rowsAffected: Any ->
            val count: Boolean = when (rowsAffected) {
                is Long -> rowsAffected > 0L
                is Int -> rowsAffected > 0
                else -> rowsAffected.toString().toLongOrNull()?.let { it > 0L } ?: false
            }
            count
        }
            .defaultIfEmpty(false)
    }

    /**
     * 콘텐츠 메타데이터를 저장합니다.
     *
     * @param metadata 저장할 메타데이터
     * @return 저장된 메타데이터 (Mono)
     */
    override fun saveMetadata(metadata: ContentMetadata): Mono<ContentMetadata> {
        return Mono.from(
            dslContext
                .insertInto(CONTENT_METADATA)
                .set(CONTENT_METADATA.CONTENT_ID, metadata.contentId.toString())
                .set(CONTENT_METADATA.TITLE, metadata.title)
                .set(CONTENT_METADATA.DESCRIPTION, metadata.description)
                .set(CONTENT_METADATA.CATEGORY, metadata.category.name)
                .set(CONTENT_METADATA.LANGUAGE, metadata.language)
                .set(CONTENT_METADATA.CREATED_AT, metadata.createdAt)
                .set(CONTENT_METADATA.CREATED_BY, metadata.createdBy?.toString())
                .set(CONTENT_METADATA.UPDATED_AT, metadata.updatedAt)
                .set(CONTENT_METADATA.UPDATED_BY, metadata.updatedBy?.toString())
                .returningResult(CONTENT_METADATA.ID)
        ).map { record ->
            val insertedId = record.getValue(CONTENT_METADATA.ID)
            logger.debug("Content metadata saved: contentId=${metadata.contentId}, id=$insertedId")
            metadata.copy(id = insertedId)
        }
    }

    /**
     * 콘텐츠 메타데이터를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 조회된 메타데이터 (없으면 empty Mono)
     */
    override fun findMetadataByContentId(contentId: UUID): Mono<ContentMetadata> {
        val tagsField = DSL.groupConcat(TAGS.NAME).separator(",").`as`("tag_names")

        return Mono.from(
            dslContext
                .select(
                    CONTENT_METADATA.ID,
                    CONTENT_METADATA.CONTENT_ID,
                    CONTENT_METADATA.TITLE,
                    CONTENT_METADATA.DESCRIPTION,
                    CONTENT_METADATA.CATEGORY,
                    CONTENT_METADATA.DIFFICULTY_LEVEL,
                    CONTENT_METADATA.LANGUAGE,
                    CONTENT_METADATA.CREATED_AT,
                    CONTENT_METADATA.CREATED_BY,
                    CONTENT_METADATA.UPDATED_AT,
                    CONTENT_METADATA.UPDATED_BY,
                    CONTENT_METADATA.DELETED_AT,
                    tagsField
                )
                .from(CONTENT_METADATA)
                .leftJoin(CONTENT_TAGS).on(
                    CONTENT_METADATA.CONTENT_ID.eq(CONTENT_TAGS.CONTENT_ID)
                        .and(CONTENT_TAGS.DELETED_AT.isNull)
                )
                .leftJoin(TAGS).on(
                    CONTENT_TAGS.TAG_ID.eq(TAGS.ID)
                        .and(TAGS.DELETED_AT.isNull)
                )
                .where(CONTENT_METADATA.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_METADATA.DELETED_AT.isNull)
                .groupBy(
                    CONTENT_METADATA.ID,
                    CONTENT_METADATA.CONTENT_ID,
                    CONTENT_METADATA.TITLE,
                    CONTENT_METADATA.DESCRIPTION,
                    CONTENT_METADATA.CATEGORY,
                    CONTENT_METADATA.DIFFICULTY_LEVEL,
                    CONTENT_METADATA.LANGUAGE,
                    CONTENT_METADATA.CREATED_AT,
                    CONTENT_METADATA.CREATED_BY,
                    CONTENT_METADATA.UPDATED_AT,
                    CONTENT_METADATA.UPDATED_BY,
                    CONTENT_METADATA.DELETED_AT
                )
        ).map { record ->
            // 태그 파싱 - GROUP_CONCAT으로 가져온 tags
            val tagsString = record.get("tag_names", String::class.java)
            val tags = if (!tagsString.isNullOrBlank()) {
                tagsString.split(",").filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            ContentMetadata(
                id = record.getValue(CONTENT_METADATA.ID),
                contentId = UUID.fromString(record.getValue(CONTENT_METADATA.CONTENT_ID)),
                title = record.getValue(CONTENT_METADATA.TITLE)!!,
                description = record.getValue(CONTENT_METADATA.DESCRIPTION),
                category = Category.valueOf(record.getValue(CONTENT_METADATA.CATEGORY)!!),
                tags = tags,
                language = record.getValue(CONTENT_METADATA.LANGUAGE)!!,
                createdAt = record.getValue(CONTENT_METADATA.CREATED_AT)!!,
                createdBy = record.getValue(CONTENT_METADATA.CREATED_BY),
                updatedAt = record.getValue(CONTENT_METADATA.UPDATED_AT)!!,
                updatedBy = record.getValue(CONTENT_METADATA.UPDATED_BY),
                deletedAt = record.getValue(CONTENT_METADATA.DELETED_AT)
            )
        }
    }

    /**
     * 콘텐츠 메타데이터를 수정합니다.
     *
     * @param metadata 수정할 메타데이터
     * @return 수정 성공 여부 (Mono)
     */
    override fun updateMetadata(metadata: ContentMetadata): Mono<Boolean> {
        return Mono.from(
            dslContext
                .update(CONTENT_METADATA)
                .set(CONTENT_METADATA.TITLE, metadata.title)
                .set(CONTENT_METADATA.DESCRIPTION, metadata.description)
                .set(CONTENT_METADATA.CATEGORY, metadata.category.name)
                .set(CONTENT_METADATA.LANGUAGE, metadata.language)
                .set(CONTENT_METADATA.UPDATED_AT, metadata.updatedAt)
                .set(CONTENT_METADATA.UPDATED_BY, metadata.updatedBy?.toString())
                .where(CONTENT_METADATA.CONTENT_ID.eq(metadata.contentId.toString()))
                .and(CONTENT_METADATA.DELETED_AT.isNull)
        ).map { rowsAffected: Any ->
            val count: Boolean = when (rowsAffected) {
                is Long -> rowsAffected > 0L
                is Int -> rowsAffected > 0
                else -> rowsAffected.toString().toLongOrNull()?.let { it > 0L } ?: false
            }
            count
        }
            .defaultIfEmpty(false)
    }

    /**
     * 크리에이터의 콘텐츠와 메타데이터를 커서 기반 페이징으로 조회합니다.
     *
     * JOIN을 사용하여 N+1 쿼리 문제를 방지합니다.
     * created_at 기준 내림차순 정렬로 최신 콘텐츠를 먼저 조회합니다.
     *
     * @param creatorId 크리에이터 ID
     * @param cursor 이전 페이지의 마지막 콘텐츠 ID (null이면 첫 페이지)
     * @param limit 페이지당 항목 수
     * @return 콘텐츠와 메타데이터의 목록 (Flux)
     */
    override fun findWithMetadataByCreatorIdWithCursor(
        creatorId: UUID,
        cursor: UUID?,
        limit: Int
    ): Flux<ContentWithMetadata> {
        val tagsField = DSL.groupConcat(TAGS.NAME).separator(",").`as`("tag_names")

        var query = dslContext
            .select(
                CONTENTS.ID,
                CONTENTS.CREATOR_ID,
                CONTENTS.CONTENT_TYPE,
                CONTENTS.URL,
                CONTENTS.THUMBNAIL_URL,
                CONTENTS.DURATION,
                CONTENTS.WIDTH,
                CONTENTS.HEIGHT,
                CONTENTS.STATUS,
                CONTENTS.CREATED_AT,
                CONTENTS.CREATED_BY,
                CONTENTS.UPDATED_AT,
                CONTENTS.UPDATED_BY,
                CONTENTS.DELETED_AT,
                CONTENT_METADATA.ID,
                CONTENT_METADATA.CONTENT_ID,
                CONTENT_METADATA.TITLE,
                CONTENT_METADATA.DESCRIPTION,
                CONTENT_METADATA.CATEGORY,
                CONTENT_METADATA.LANGUAGE,
                CONTENT_METADATA.CREATED_AT,
                CONTENT_METADATA.CREATED_BY,
                CONTENT_METADATA.UPDATED_AT,
                CONTENT_METADATA.UPDATED_BY,
                CONTENT_METADATA.DELETED_AT,
                tagsField
            )
            .from(CONTENTS)
            .innerJoin(CONTENT_METADATA).on(CONTENTS.ID.eq(CONTENT_METADATA.CONTENT_ID))
            .leftJoin(CONTENT_TAGS).on(
                CONTENTS.ID.eq(CONTENT_TAGS.CONTENT_ID)
                    .and(CONTENT_TAGS.DELETED_AT.isNull)
            )
            .leftJoin(TAGS).on(
                CONTENT_TAGS.TAG_ID.eq(TAGS.ID)
                    .and(TAGS.DELETED_AT.isNull)
            )
            .where(CONTENTS.CREATOR_ID.eq(creatorId.toString()))
            .and(CONTENTS.DELETED_AT.isNull)
            .and(CONTENT_METADATA.DELETED_AT.isNull)

        // 커서 기반 페이지네이션 (created_at 기준)
        if (cursor != null) {
            query = query.and(
                CONTENTS.CREATED_AT.lt(
                    dslContext.select(CONTENTS.CREATED_AT)
                        .from(CONTENTS)
                        .where(CONTENTS.ID.eq(cursor.toString()))
                        .asField()
                )
            )
        }

        return Flux.from(
            query
                .groupBy(
                    CONTENTS.ID,
                    CONTENTS.CREATOR_ID,
                    CONTENTS.CONTENT_TYPE,
                    CONTENTS.URL,
                    CONTENTS.THUMBNAIL_URL,
                    CONTENTS.DURATION,
                    CONTENTS.WIDTH,
                    CONTENTS.HEIGHT,
                    CONTENTS.STATUS,
                    CONTENTS.CREATED_AT,
                    CONTENTS.CREATED_BY,
                    CONTENTS.UPDATED_AT,
                    CONTENTS.UPDATED_BY,
                    CONTENTS.DELETED_AT,
                    CONTENT_METADATA.ID,
                    CONTENT_METADATA.CONTENT_ID,
                    CONTENT_METADATA.TITLE,
                    CONTENT_METADATA.DESCRIPTION,
                    CONTENT_METADATA.CATEGORY,
                    CONTENT_METADATA.LANGUAGE,
                    CONTENT_METADATA.CREATED_AT,
                    CONTENT_METADATA.CREATED_BY,
                    CONTENT_METADATA.UPDATED_AT,
                    CONTENT_METADATA.UPDATED_BY,
                    CONTENT_METADATA.DELETED_AT
                )
                .orderBy(CONTENTS.CREATED_AT.desc())
                .limit(limit)
        ).map { record ->
            val content = Content(
                id = UUID.fromString(record.getValue(CONTENTS.ID)),
                creatorId = UUID.fromString(record.getValue(CONTENTS.CREATOR_ID)),
                contentType = ContentType.valueOf(record.getValue(CONTENTS.CONTENT_TYPE)!!),
                url = record.getValue(CONTENTS.URL)!!,
                thumbnailUrl = record.getValue(CONTENTS.THUMBNAIL_URL)!!,
                duration = record.getValue(CONTENTS.DURATION),
                width = record.getValue(CONTENTS.WIDTH)!!,
                height = record.getValue(CONTENTS.HEIGHT)!!,
                status = ContentStatus.valueOf(record.getValue(CONTENTS.STATUS)!!),
                createdAt = record.getValue(CONTENTS.CREATED_AT)!!,
                createdBy = record.getValue(CONTENTS.CREATED_BY),
                updatedAt = record.getValue(CONTENTS.UPDATED_AT)!!,
                updatedBy = record.getValue(CONTENTS.UPDATED_BY),
                deletedAt = record.getValue(CONTENTS.DELETED_AT)
            )

            // 태그 파싱 - GROUP_CONCAT으로 가져온 tags
            val tagsString = record.get("tag_names", String::class.java)
            val tags = if (!tagsString.isNullOrBlank()) {
                tagsString.split(",").filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val metadata = ContentMetadata(
                id = record.getValue(CONTENT_METADATA.ID),
                contentId = UUID.fromString(record.getValue(CONTENT_METADATA.CONTENT_ID)),
                title = record.getValue(CONTENT_METADATA.TITLE)!!,
                description = record.getValue(CONTENT_METADATA.DESCRIPTION),
                category = Category.valueOf(record.getValue(CONTENT_METADATA.CATEGORY)!!),
                tags = tags,
                language = record.getValue(CONTENT_METADATA.LANGUAGE)!!,
                createdAt = record.getValue(CONTENT_METADATA.CREATED_AT)!!,
                createdBy = record.getValue(CONTENT_METADATA.CREATED_BY),
                updatedAt = record.getValue(CONTENT_METADATA.UPDATED_AT)!!,
                updatedBy = record.getValue(CONTENT_METADATA.UPDATED_BY),
                deletedAt = record.getValue(CONTENT_METADATA.DELETED_AT)
            )

            ContentWithMetadata(content, metadata)
        }
    }

    /**
     * 여러 콘텐츠 ID로 콘텐츠와 메타데이터를 배치 조회합니다.
     *
     * JOIN을 사용하여 N+1 쿼리 문제를 방지합니다.
     * 저장한 콘텐츠 목록 조회 등에서 사용됩니다.
     *
     * @param contentIds 조회할 콘텐츠 ID 목록
     * @return 콘텐츠와 메타데이터의 목록 (Flux)
     */
    override fun findByIdsWithMetadata(contentIds: List<UUID>): Flux<ContentWithMetadata> {
        if (contentIds.isEmpty()) {
            return Flux.empty()
        }

        val contentIdsString = contentIds.map { it.toString() }
        val tagsField = DSL.groupConcat(TAGS.NAME).separator(",").`as`("tag_names")

        return Flux.from(
            dslContext
                .select(
                    CONTENTS.ID,
                    CONTENTS.CREATOR_ID,
                    CONTENTS.CONTENT_TYPE,
                    CONTENTS.URL,
                    CONTENTS.THUMBNAIL_URL,
                    CONTENTS.DURATION,
                    CONTENTS.WIDTH,
                    CONTENTS.HEIGHT,
                    CONTENTS.STATUS,
                    CONTENTS.CREATED_AT,
                    CONTENTS.CREATED_BY,
                    CONTENTS.UPDATED_AT,
                    CONTENTS.UPDATED_BY,
                    CONTENTS.DELETED_AT,
                    CONTENT_METADATA.ID,
                    CONTENT_METADATA.CONTENT_ID,
                    CONTENT_METADATA.TITLE,
                    CONTENT_METADATA.DESCRIPTION,
                    CONTENT_METADATA.CATEGORY,
                    CONTENT_METADATA.DIFFICULTY_LEVEL,
                    CONTENT_METADATA.LANGUAGE,
                    CONTENT_METADATA.CREATED_AT,
                    CONTENT_METADATA.CREATED_BY,
                    CONTENT_METADATA.UPDATED_AT,
                    CONTENT_METADATA.UPDATED_BY,
                    CONTENT_METADATA.DELETED_AT,
                    tagsField
                )
                .from(CONTENTS)
                .innerJoin(CONTENT_METADATA).on(CONTENTS.ID.eq(CONTENT_METADATA.CONTENT_ID))
                .leftJoin(CONTENT_TAGS).on(
                    CONTENTS.ID.eq(CONTENT_TAGS.CONTENT_ID)
                        .and(CONTENT_TAGS.DELETED_AT.isNull)
                )
                .leftJoin(TAGS).on(
                    CONTENT_TAGS.TAG_ID.eq(TAGS.ID)
                        .and(TAGS.DELETED_AT.isNull)
                )
                .where(CONTENTS.ID.`in`(contentIdsString))
                .and(CONTENTS.DELETED_AT.isNull)
                .and(CONTENT_METADATA.DELETED_AT.isNull)
                .groupBy(
                    CONTENTS.ID,
                    CONTENTS.CREATOR_ID,
                    CONTENTS.CONTENT_TYPE,
                    CONTENTS.URL,
                    CONTENTS.THUMBNAIL_URL,
                    CONTENTS.DURATION,
                    CONTENTS.WIDTH,
                    CONTENTS.HEIGHT,
                    CONTENTS.STATUS,
                    CONTENTS.CREATED_AT,
                    CONTENTS.CREATED_BY,
                    CONTENTS.UPDATED_AT,
                    CONTENTS.UPDATED_BY,
                    CONTENTS.DELETED_AT,
                    CONTENT_METADATA.ID,
                    CONTENT_METADATA.CONTENT_ID,
                    CONTENT_METADATA.TITLE,
                    CONTENT_METADATA.DESCRIPTION,
                    CONTENT_METADATA.CATEGORY,
                    CONTENT_METADATA.DIFFICULTY_LEVEL,
                    CONTENT_METADATA.LANGUAGE,
                    CONTENT_METADATA.CREATED_AT,
                    CONTENT_METADATA.CREATED_BY,
                    CONTENT_METADATA.UPDATED_AT,
                    CONTENT_METADATA.UPDATED_BY,
                    CONTENT_METADATA.DELETED_AT
                )
        ).map { record ->
            val content = Content(
                id = UUID.fromString(record.getValue(CONTENTS.ID)),
                creatorId = UUID.fromString(record.getValue(CONTENTS.CREATOR_ID)),
                contentType = ContentType.valueOf(record.getValue(CONTENTS.CONTENT_TYPE)!!),
                url = record.getValue(CONTENTS.URL)!!,
                thumbnailUrl = record.getValue(CONTENTS.THUMBNAIL_URL)!!,
                duration = record.getValue(CONTENTS.DURATION),
                width = record.getValue(CONTENTS.WIDTH)!!,
                height = record.getValue(CONTENTS.HEIGHT)!!,
                status = ContentStatus.valueOf(record.getValue(CONTENTS.STATUS)!!),
                createdAt = record.getValue(CONTENTS.CREATED_AT)!!,
                createdBy = record.getValue(CONTENTS.CREATED_BY),
                updatedAt = record.getValue(CONTENTS.UPDATED_AT)!!,
                updatedBy = record.getValue(CONTENTS.UPDATED_BY),
                deletedAt = record.getValue(CONTENTS.DELETED_AT)
            )

            // 태그 파싱 - GROUP_CONCAT으로 가져온 tags
            val tagsString = record.get("tag_names", String::class.java)
            val tags = if (!tagsString.isNullOrBlank()) {
                tagsString.split(",").filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val metadata = ContentMetadata(
                id = record.getValue(CONTENT_METADATA.ID),
                contentId = UUID.fromString(record.getValue(CONTENT_METADATA.CONTENT_ID)),
                title = record.getValue(CONTENT_METADATA.TITLE)!!,
                description = record.getValue(CONTENT_METADATA.DESCRIPTION),
                category = Category.valueOf(record.getValue(CONTENT_METADATA.CATEGORY)!!),
                tags = tags,
                language = record.getValue(CONTENT_METADATA.LANGUAGE)!!,
                createdAt = record.getValue(CONTENT_METADATA.CREATED_AT)!!,
                createdBy = record.getValue(CONTENT_METADATA.CREATED_BY),
                updatedAt = record.getValue(CONTENT_METADATA.UPDATED_AT)!!,
                updatedBy = record.getValue(CONTENT_METADATA.UPDATED_BY),
                deletedAt = record.getValue(CONTENT_METADATA.DELETED_AT)
            )

            ContentWithMetadata(content, metadata)
        }
    }

    /**
     * 제목으로 콘텐츠 검색 (LIKE 검색)
     *
     * Manticore Search Failover용 DB 검색 메서드입니다.
     * 제목에 검색어가 포함된 콘텐츠를 조회합니다.
     *
     * @param query 검색 키워드
     * @param limit 최대 조회 개수
     * @return 콘텐츠 ID 목록 (최신순)
     */
    override fun searchByTitle(query: String, limit: Int): Flux<UUID> {
        return Flux.from(
            dslContext
                .select(CONTENT_METADATA.CONTENT_ID)
                .from(CONTENT_METADATA)
                .innerJoin(CONTENTS).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
                .where(CONTENT_METADATA.TITLE.like("%$query%"))
                .and(CONTENT_METADATA.DELETED_AT.isNull)
                .and(CONTENTS.DELETED_AT.isNull)
                .and(CONTENTS.STATUS.eq("PUBLISHED"))  // 발행된 콘텐츠만
                .orderBy(CONTENTS.CREATED_AT.desc())
                .limit(limit + 1)  // hasNext 확인을 위해 +1
        ).map { record -> UUID.fromString(record.getValue(CONTENT_METADATA.CONTENT_ID)) }
    }

    /**
     * 크리에이터의 총 콘텐츠 개수를 조회합니다.
     *
     * 프로필 화면에서 사용자의 총 콘텐츠 개수를 표시하기 위해 사용됩니다.
     * Soft Delete되지 않은 콘텐츠만 카운트합니다.
     *
     * @param creatorId 크리에이터 ID
     * @return 콘텐츠 개수 (Mono)
     */
    override fun countByCreatorId(creatorId: UUID): Mono<Long> {
        return Mono.from(
            dslContext
                .selectCount()
                .from(CONTENTS)
                .where(CONTENTS.CREATOR_ID.eq(creatorId.toString()))
                .and(CONTENTS.DELETED_AT.isNull)
        ).map { record ->
            record.getValue(0, Long::class.java) ?: 0L
        }
            .defaultIfEmpty(0L)
    }
}
