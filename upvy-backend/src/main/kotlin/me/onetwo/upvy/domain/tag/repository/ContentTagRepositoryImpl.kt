package me.onetwo.upvy.domain.tag.repository

import me.onetwo.upvy.domain.tag.model.ContentTag
import me.onetwo.upvy.jooq.generated.tables.references.CONTENT_TAGS
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 콘텐츠-태그 관계 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class ContentTagRepositoryImpl(
    private val dslContext: DSLContext
) : ContentTagRepository {

    private val logger = LoggerFactory.getLogger(ContentTagRepositoryImpl::class.java)

    override fun save(contentTag: ContentTag): Mono<ContentTag> {
        return Mono.from(
            dslContext
                .insertInto(CONTENT_TAGS)
                .set(CONTENT_TAGS.CONTENT_ID, contentTag.contentId.toString())
                .set(CONTENT_TAGS.TAG_ID, contentTag.tagId)
                .set(CONTENT_TAGS.CREATED_AT, contentTag.createdAt)
                .set(CONTENT_TAGS.CREATED_BY, contentTag.createdBy)
                .set(CONTENT_TAGS.UPDATED_AT, contentTag.updatedAt)
                .set(CONTENT_TAGS.UPDATED_BY, contentTag.updatedBy)
                .returningResult(CONTENT_TAGS.ID)
        ).map { record ->
            val generatedId = record.getValue(CONTENT_TAGS.ID)
            logger.debug("ContentTag saved: id=$generatedId, contentId=${contentTag.contentId}, tagId=${contentTag.tagId}")
            contentTag.copy(id = generatedId)
        }
    }

    override fun saveAll(contentTags: List<ContentTag>): Flux<ContentTag> {
        if (contentTags.isEmpty()) {
            return Flux.empty()
        }

        val queries = contentTags.map { contentTag ->
            dslContext
                .insertInto(CONTENT_TAGS)
                .set(CONTENT_TAGS.CONTENT_ID, contentTag.contentId.toString())
                .set(CONTENT_TAGS.TAG_ID, contentTag.tagId)
                .set(CONTENT_TAGS.CREATED_AT, contentTag.createdAt)
                .set(CONTENT_TAGS.CREATED_BY, contentTag.createdBy)
                .set(CONTENT_TAGS.UPDATED_AT, contentTag.updatedAt)
                .set(CONTENT_TAGS.UPDATED_BY, contentTag.updatedBy)
                .returningResult(CONTENT_TAGS.ID)
        }

        return Flux.concat(queries.map { query -> Mono.from(query) })
            .zipWithIterable(contentTags) { record, contentTag ->
                val generatedId = record.getValue(CONTENT_TAGS.ID)
                contentTag.copy(id = generatedId)
            }
    }

    override fun findTagIdsByContentId(contentId: UUID): Flux<Long> {
        return Flux.from(
            dslContext
                .select(CONTENT_TAGS.TAG_ID)
                .from(CONTENT_TAGS)
                .where(CONTENT_TAGS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_TAGS.DELETED_AT.isNull)
        ).map { record ->
            record.getValue(CONTENT_TAGS.TAG_ID)!!
        }
    }

    override fun findByContentId(contentId: UUID): Flux<ContentTag> {
        return Flux.from(
            dslContext
                .select(
                    CONTENT_TAGS.ID,
                    CONTENT_TAGS.CONTENT_ID,
                    CONTENT_TAGS.TAG_ID,
                    CONTENT_TAGS.CREATED_AT,
                    CONTENT_TAGS.CREATED_BY,
                    CONTENT_TAGS.UPDATED_AT,
                    CONTENT_TAGS.UPDATED_BY,
                    CONTENT_TAGS.DELETED_AT
                )
                .from(CONTENT_TAGS)
                .where(CONTENT_TAGS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_TAGS.DELETED_AT.isNull)
        ).map { record ->
            ContentTag(
                id = record.getValue(CONTENT_TAGS.ID),
                contentId = UUID.fromString(record.getValue(CONTENT_TAGS.CONTENT_ID)!!),
                tagId = record.getValue(CONTENT_TAGS.TAG_ID)!!,
                createdAt = record.getValue(CONTENT_TAGS.CREATED_AT) ?: Instant.now(),
                createdBy = record.getValue(CONTENT_TAGS.CREATED_BY),
                updatedAt = record.getValue(CONTENT_TAGS.UPDATED_AT) ?: Instant.now(),
                updatedBy = record.getValue(CONTENT_TAGS.UPDATED_BY),
                deletedAt = record.getValue(CONTENT_TAGS.DELETED_AT)
            )
        }
    }

    override fun findContentIdsByTagId(tagId: Long): Flux<UUID> {
        return Flux.from(
            dslContext
                .select(CONTENT_TAGS.CONTENT_ID)
                .from(CONTENT_TAGS)
                .where(CONTENT_TAGS.TAG_ID.eq(tagId))
                .and(CONTENT_TAGS.DELETED_AT.isNull)
        ).map { record ->
            UUID.fromString(record.getValue(CONTENT_TAGS.CONTENT_ID)!!)
        }
    }

    override fun existsByContentIdAndTagId(contentId: UUID, tagId: Long): Mono<Boolean> {
        return Mono.from(
            dslContext
                .selectCount()
                .from(CONTENT_TAGS)
                .where(CONTENT_TAGS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_TAGS.TAG_ID.eq(tagId))
                .and(CONTENT_TAGS.DELETED_AT.isNull)
        ).map { record ->
            val count = record.value1()
            count > 0
        }.defaultIfEmpty(false)
    }

    override fun deleteByContentId(contentId: UUID, deletedBy: String): Mono<Int> {
        return Mono.from(
            dslContext
                .update(CONTENT_TAGS)
                .set(CONTENT_TAGS.DELETED_AT, Instant.now())
                .set(CONTENT_TAGS.UPDATED_AT, Instant.now())
                .set(CONTENT_TAGS.UPDATED_BY, deletedBy)
                .where(CONTENT_TAGS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_TAGS.DELETED_AT.isNull)
        ).map { rowsUpdated ->
            logger.debug("ContentTags deleted by contentId: contentId=$contentId, count=$rowsUpdated")
            rowsUpdated
        }.defaultIfEmpty(0)
    }

    override fun deleteByContentIdAndTagId(contentId: UUID, tagId: Long, deletedBy: String): Mono<Boolean> {
        return Mono.from(
            dslContext
                .update(CONTENT_TAGS)
                .set(CONTENT_TAGS.DELETED_AT, Instant.now())
                .set(CONTENT_TAGS.UPDATED_AT, Instant.now())
                .set(CONTENT_TAGS.UPDATED_BY, deletedBy)
                .where(CONTENT_TAGS.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENT_TAGS.TAG_ID.eq(tagId))
                .and(CONTENT_TAGS.DELETED_AT.isNull)
        ).map { rowsUpdated ->
            val success = rowsUpdated > 0
            logger.debug("ContentTag deleted: contentId=$contentId, tagId=$tagId, success=$success")
            success
        }.defaultIfEmpty(false)
    }
}
