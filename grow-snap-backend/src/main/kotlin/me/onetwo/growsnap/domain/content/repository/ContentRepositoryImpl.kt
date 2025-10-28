package me.onetwo.growsnap.domain.content.repository

import com.fasterxml.jackson.databind.ObjectMapper
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.Content
import me.onetwo.growsnap.domain.content.model.ContentMetadata
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.content.model.DifficultyLevel
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENTS
import me.onetwo.growsnap.jooq.generated.tables.references.CONTENT_METADATA
import org.jooq.DSLContext
import org.jooq.JSON
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

/**
 * 콘텐츠 레포지토리 구현체
 *
 * JOOQ를 사용하여 콘텐츠 데이터베이스 CRUD를 담당합니다.
 *
 * @property dslContext JOOQ DSL Context
 * @property objectMapper JSON 변환을 위한 ObjectMapper
 */
@Repository
class ContentRepositoryImpl(
    private val dslContext: DSLContext,
    private val objectMapper: ObjectMapper
) : ContentRepository {

    private val logger = LoggerFactory.getLogger(ContentRepositoryImpl::class.java)

    /**
     * 콘텐츠를 저장합니다.
     *
     * @param content 저장할 콘텐츠
     * @return 저장된 콘텐츠
     */
    override fun save(content: Content): Content? {
        return try {
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
                .execute()

            logger.debug("Content saved: contentId=${content.id}")
            content
        } catch (e: Exception) {
            logger.error("Failed to save content: contentId=${content.id}", e)
            null
        }
    }

    /**
     * 콘텐츠를 ID로 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 조회된 콘텐츠 (없으면 null)
     */
    override fun findById(contentId: UUID): Content? {
        return dslContext
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
            .fetchOne()
            ?.let { record ->
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
                    createdBy = record.getValue(CONTENTS.CREATED_BY)?.let { UUID.fromString(it) },
                    updatedAt = record.getValue(CONTENTS.UPDATED_AT)!!,
                    updatedBy = record.getValue(CONTENTS.UPDATED_BY)?.let { UUID.fromString(it) },
                    deletedAt = record.getValue(CONTENTS.DELETED_AT)
                )
            }
    }

    /**
     * 크리에이터의 콘텐츠 목록을 조회합니다.
     *
     * @param creatorId 크리에이터 ID
     * @return 콘텐츠 목록
     */
    override fun findByCreatorId(creatorId: UUID): List<Content> {
        return dslContext
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
            .fetch()
            .map { record ->
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
                    createdBy = record.getValue(CONTENTS.CREATED_BY)?.let { UUID.fromString(it) },
                    updatedAt = record.getValue(CONTENTS.UPDATED_AT)!!,
                    updatedBy = record.getValue(CONTENTS.UPDATED_BY)?.let { UUID.fromString(it) },
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
     * @return 콘텐츠와 메타데이터 쌍의 목록
     */
    override fun findWithMetadataByCreatorId(creatorId: UUID): List<Pair<Content, ContentMetadata>> {
        return dslContext
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
                CONTENT_METADATA.TAGS,
                CONTENT_METADATA.LANGUAGE,
                CONTENT_METADATA.CREATED_AT,
                CONTENT_METADATA.CREATED_BY,
                CONTENT_METADATA.UPDATED_AT,
                CONTENT_METADATA.UPDATED_BY,
                CONTENT_METADATA.DELETED_AT
            )
            .from(CONTENTS)
            .innerJoin(CONTENT_METADATA).on(CONTENTS.ID.eq(CONTENT_METADATA.CONTENT_ID))
            .where(CONTENTS.CREATOR_ID.eq(creatorId.toString()))
            .and(CONTENTS.DELETED_AT.isNull)
            .and(CONTENT_METADATA.DELETED_AT.isNull)
            .orderBy(CONTENTS.CREATED_AT.desc())
            .fetch()
            .map { record ->
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
                    createdBy = record.getValue(CONTENTS.CREATED_BY)?.let { UUID.fromString(it) },
                    updatedAt = record.getValue(CONTENTS.UPDATED_AT)!!,
                    updatedBy = record.getValue(CONTENTS.UPDATED_BY)?.let { UUID.fromString(it) },
                    deletedAt = record.getValue(CONTENTS.DELETED_AT)
                )

                val tagsJson = record.getValue(CONTENT_METADATA.TAGS)
                val tags = if (tagsJson != null) {
                    // tagsJson.data()가 문자열을 반환하므로, 먼저 String으로 읽고 다시 JSON으로 파싱
                    val jsonString = objectMapper.readValue(tagsJson.data(), String::class.java)
                    @Suppress("UNCHECKED_CAST")
                    objectMapper.readValue(jsonString, List::class.java) as List<String>
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
                    createdBy = record.getValue(CONTENT_METADATA.CREATED_BY)?.let { UUID.fromString(it) },
                    updatedAt = record.getValue(CONTENT_METADATA.UPDATED_AT)!!,
                    updatedBy = record.getValue(CONTENT_METADATA.UPDATED_BY)?.let { UUID.fromString(it) },
                    deletedAt = record.getValue(CONTENT_METADATA.DELETED_AT)
                )

                Pair(content, metadata)
            }
    }

    /**
     * 콘텐츠를 수정합니다.
     *
     * @param content 수정할 콘텐츠
     * @return 수정 성공 여부
     */
    override fun update(content: Content): Boolean {
        val rowsAffected = dslContext
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
            .execute()

        return rowsAffected > 0
    }

    /**
     * 콘텐츠를 삭제합니다 (Soft Delete).
     *
     * @param contentId 삭제할 콘텐츠 ID
     * @param deletedBy 삭제한 사용자 ID
     * @return 삭제 성공 여부
     */
    override fun delete(contentId: UUID, deletedBy: UUID): Boolean {
        val rowsAffected = dslContext
            .update(CONTENTS)
            .set(CONTENTS.DELETED_AT, LocalDateTime.now())
            .set(CONTENTS.UPDATED_AT, LocalDateTime.now())
            .set(CONTENTS.UPDATED_BY, deletedBy.toString())
            .where(CONTENTS.ID.eq(contentId.toString()))
            .and(CONTENTS.DELETED_AT.isNull)
            .execute()

        return rowsAffected > 0
    }

    /**
     * 콘텐츠 메타데이터를 저장합니다.
     *
     * @param metadata 저장할 메타데이터
     * @return 저장된 메타데이터
     */
    override fun saveMetadata(metadata: ContentMetadata): ContentMetadata? {
        return try {
            val tagsJson = JSON.valueOf(objectMapper.writeValueAsString(metadata.tags))

            val insertedId = dslContext
                .insertInto(CONTENT_METADATA)
                .set(CONTENT_METADATA.CONTENT_ID, metadata.contentId.toString())
                .set(CONTENT_METADATA.TITLE, metadata.title)
                .set(CONTENT_METADATA.DESCRIPTION, metadata.description)
                .set(CONTENT_METADATA.CATEGORY, metadata.category.name)
                .set(CONTENT_METADATA.TAGS, tagsJson)
                .set(CONTENT_METADATA.LANGUAGE, metadata.language)
                .set(CONTENT_METADATA.CREATED_AT, metadata.createdAt)
                .set(CONTENT_METADATA.CREATED_BY, metadata.createdBy?.toString())
                .set(CONTENT_METADATA.UPDATED_AT, metadata.updatedAt)
                .set(CONTENT_METADATA.UPDATED_BY, metadata.updatedBy?.toString())
                .returningResult(CONTENT_METADATA.ID)
                .fetchOne()
                ?.getValue(CONTENT_METADATA.ID)

            logger.debug("Content metadata saved: contentId=${metadata.contentId}, id=$insertedId")

            metadata.copy(id = insertedId)
        } catch (e: Exception) {
            logger.error("Failed to save content metadata: contentId=${metadata.contentId}", e)
            null
        }
    }

    /**
     * 콘텐츠 메타데이터를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 조회된 메타데이터 (없으면 null)
     */
    override fun findMetadataByContentId(contentId: UUID): ContentMetadata? {
        return dslContext
            .select(
                CONTENT_METADATA.ID,
                CONTENT_METADATA.CONTENT_ID,
                CONTENT_METADATA.TITLE,
                CONTENT_METADATA.DESCRIPTION,
                CONTENT_METADATA.CATEGORY,
                CONTENT_METADATA.TAGS,
                CONTENT_METADATA.DIFFICULTY_LEVEL,
                CONTENT_METADATA.LANGUAGE,
                CONTENT_METADATA.CREATED_AT,
                CONTENT_METADATA.CREATED_BY,
                CONTENT_METADATA.UPDATED_AT,
                CONTENT_METADATA.UPDATED_BY,
                CONTENT_METADATA.DELETED_AT
            )
            .from(CONTENT_METADATA)
            .where(CONTENT_METADATA.CONTENT_ID.eq(contentId.toString()))
            .and(CONTENT_METADATA.DELETED_AT.isNull)
            .fetchOne()
            ?.let { record ->
                val tagsJson = record.getValue(CONTENT_METADATA.TAGS)
                val tags = if (tagsJson != null) {
                    // tagsJson.data()가 문자열을 반환하므로, 먼저 String으로 읽고 다시 JSON으로 파싱
                    val jsonString = objectMapper.readValue(tagsJson.data(), String::class.java)
                    @Suppress("UNCHECKED_CAST")
                    objectMapper.readValue(jsonString, List::class.java) as List<String>
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
                    createdBy = record.getValue(CONTENT_METADATA.CREATED_BY)?.let { UUID.fromString(it) },
                    updatedAt = record.getValue(CONTENT_METADATA.UPDATED_AT)!!,
                    updatedBy = record.getValue(CONTENT_METADATA.UPDATED_BY)?.let { UUID.fromString(it) },
                    deletedAt = record.getValue(CONTENT_METADATA.DELETED_AT)
                )
            }
    }

    /**
     * 콘텐츠 메타데이터를 수정합니다.
     *
     * @param metadata 수정할 메타데이터
     * @return 수정 성공 여부
     */
    override fun updateMetadata(metadata: ContentMetadata): Boolean {
        val tagsJson = JSON.valueOf(objectMapper.writeValueAsString(metadata.tags))

        val rowsAffected = dslContext
            .update(CONTENT_METADATA)
            .set(CONTENT_METADATA.TITLE, metadata.title)
            .set(CONTENT_METADATA.DESCRIPTION, metadata.description)
            .set(CONTENT_METADATA.CATEGORY, metadata.category.name)
            .set(CONTENT_METADATA.TAGS, tagsJson)
            .set(CONTENT_METADATA.LANGUAGE, metadata.language)
            .set(CONTENT_METADATA.UPDATED_AT, metadata.updatedAt)
            .set(CONTENT_METADATA.UPDATED_BY, metadata.updatedBy?.toString())
            .where(CONTENT_METADATA.CONTENT_ID.eq(metadata.contentId.toString()))
            .and(CONTENT_METADATA.DELETED_AT.isNull)
            .execute()

        return rowsAffected > 0
    }
}
