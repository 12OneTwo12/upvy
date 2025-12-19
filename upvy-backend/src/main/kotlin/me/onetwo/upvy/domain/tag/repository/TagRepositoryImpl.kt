package me.onetwo.upvy.domain.tag.repository

import me.onetwo.upvy.domain.tag.model.Tag
import me.onetwo.upvy.jooq.generated.tables.references.TAGS
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * 태그 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class TagRepositoryImpl(
    private val dslContext: DSLContext
) : TagRepository {

    private val logger = LoggerFactory.getLogger(TagRepositoryImpl::class.java)

    override fun save(tag: Tag): Mono<Tag> {
        return Mono.from(
            dslContext
                .insertInto(TAGS)
                .set(TAGS.NAME, tag.name)
                .set(TAGS.NORMALIZED_NAME, tag.normalizedName)
                .set(TAGS.USAGE_COUNT, tag.usageCount)
                .set(TAGS.CREATED_AT, tag.createdAt)
                .set(TAGS.CREATED_BY, tag.createdBy)
                .set(TAGS.UPDATED_AT, tag.updatedAt)
                .set(TAGS.UPDATED_BY, tag.updatedBy)
                .returningResult(TAGS.ID)
        ).map { record ->
            val generatedId = record.getValue(TAGS.ID)
            logger.debug("Tag saved: tagId=$generatedId, name=${tag.name}")
            tag.copy(id = generatedId)
        }
    }

    override fun findById(tagId: Long): Mono<Tag> {
        return Mono.from(
            dslContext
                .select(
                    TAGS.ID,
                    TAGS.NAME,
                    TAGS.NORMALIZED_NAME,
                    TAGS.USAGE_COUNT,
                    TAGS.CREATED_AT,
                    TAGS.CREATED_BY,
                    TAGS.UPDATED_AT,
                    TAGS.UPDATED_BY,
                    TAGS.DELETED_AT
                )
                .from(TAGS)
                .where(TAGS.ID.eq(tagId))
                .and(TAGS.DELETED_AT.isNull)
        ).map { record ->
            Tag(
                id = record.getValue(TAGS.ID),
                name = record.getValue(TAGS.NAME)!!,
                normalizedName = record.getValue(TAGS.NORMALIZED_NAME)!!,
                usageCount = (record.getValue(TAGS.USAGE_COUNT) as? Number)?.toInt() ?: 0,
                createdAt = record.getValue(TAGS.CREATED_AT) ?: Instant.now(),
                createdBy = record.getValue(TAGS.CREATED_BY),
                updatedAt = record.getValue(TAGS.UPDATED_AT) ?: Instant.now(),
                updatedBy = record.getValue(TAGS.UPDATED_BY),
                deletedAt = record.getValue(TAGS.DELETED_AT)
            )
        }
    }

    override fun findByName(name: String): Mono<Tag> {
        return Mono.from(
            dslContext
                .select(
                    TAGS.ID,
                    TAGS.NAME,
                    TAGS.NORMALIZED_NAME,
                    TAGS.USAGE_COUNT,
                    TAGS.CREATED_AT,
                    TAGS.CREATED_BY,
                    TAGS.UPDATED_AT,
                    TAGS.UPDATED_BY,
                    TAGS.DELETED_AT
                )
                .from(TAGS)
                .where(TAGS.NAME.eq(name))
                .and(TAGS.DELETED_AT.isNull)
        ).map { record ->
            Tag(
                id = record.getValue(TAGS.ID),
                name = record.getValue(TAGS.NAME)!!,
                normalizedName = record.getValue(TAGS.NORMALIZED_NAME)!!,
                usageCount = (record.getValue(TAGS.USAGE_COUNT) as? Number)?.toInt() ?: 0,
                createdAt = record.getValue(TAGS.CREATED_AT) ?: Instant.now(),
                createdBy = record.getValue(TAGS.CREATED_BY),
                updatedAt = record.getValue(TAGS.UPDATED_AT) ?: Instant.now(),
                updatedBy = record.getValue(TAGS.UPDATED_BY),
                deletedAt = record.getValue(TAGS.DELETED_AT)
            )
        }
    }

    override fun findByNormalizedName(normalizedName: String): Mono<Tag> {
        return Mono.from(
            dslContext
                .select(
                    TAGS.ID,
                    TAGS.NAME,
                    TAGS.NORMALIZED_NAME,
                    TAGS.USAGE_COUNT,
                    TAGS.CREATED_AT,
                    TAGS.CREATED_BY,
                    TAGS.UPDATED_AT,
                    TAGS.UPDATED_BY,
                    TAGS.DELETED_AT
                )
                .from(TAGS)
                .where(TAGS.NORMALIZED_NAME.eq(normalizedName))
                .and(TAGS.DELETED_AT.isNull)
        ).map { record ->
            Tag(
                id = record.getValue(TAGS.ID),
                name = record.getValue(TAGS.NAME)!!,
                normalizedName = record.getValue(TAGS.NORMALIZED_NAME)!!,
                usageCount = (record.getValue(TAGS.USAGE_COUNT) as? Number)?.toInt() ?: 0,
                createdAt = record.getValue(TAGS.CREATED_AT) ?: Instant.now(),
                createdBy = record.getValue(TAGS.CREATED_BY),
                updatedAt = record.getValue(TAGS.UPDATED_AT) ?: Instant.now(),
                updatedBy = record.getValue(TAGS.UPDATED_BY),
                deletedAt = record.getValue(TAGS.DELETED_AT)
            )
        }
    }

    override fun findByIds(tagIds: List<Long>): Flux<Tag> {
        if (tagIds.isEmpty()) {
            return Flux.empty()
        }

        return Flux.from(
            dslContext
                .select(
                    TAGS.ID,
                    TAGS.NAME,
                    TAGS.NORMALIZED_NAME,
                    TAGS.USAGE_COUNT,
                    TAGS.CREATED_AT,
                    TAGS.CREATED_BY,
                    TAGS.UPDATED_AT,
                    TAGS.UPDATED_BY,
                    TAGS.DELETED_AT
                )
                .from(TAGS)
                .where(TAGS.ID.`in`(tagIds))
                .and(TAGS.DELETED_AT.isNull)
        ).map { record ->
            Tag(
                id = record.getValue(TAGS.ID),
                name = record.getValue(TAGS.NAME)!!,
                normalizedName = record.getValue(TAGS.NORMALIZED_NAME)!!,
                usageCount = (record.getValue(TAGS.USAGE_COUNT) as? Number)?.toInt() ?: 0,
                createdAt = record.getValue(TAGS.CREATED_AT) ?: Instant.now(),
                createdBy = record.getValue(TAGS.CREATED_BY),
                updatedAt = record.getValue(TAGS.UPDATED_AT) ?: Instant.now(),
                updatedBy = record.getValue(TAGS.UPDATED_BY),
                deletedAt = record.getValue(TAGS.DELETED_AT)
            )
        }
    }

    override fun findPopularTags(limit: Int): Flux<Tag> {
        return Flux.from(
            dslContext
                .select(
                    TAGS.ID,
                    TAGS.NAME,
                    TAGS.NORMALIZED_NAME,
                    TAGS.USAGE_COUNT,
                    TAGS.CREATED_AT,
                    TAGS.CREATED_BY,
                    TAGS.UPDATED_AT,
                    TAGS.UPDATED_BY,
                    TAGS.DELETED_AT
                )
                .from(TAGS)
                .where(TAGS.DELETED_AT.isNull)
                .orderBy(TAGS.USAGE_COUNT.desc())
                .limit(limit)
        ).map { record ->
            Tag(
                id = record.getValue(TAGS.ID),
                name = record.getValue(TAGS.NAME)!!,
                normalizedName = record.getValue(TAGS.NORMALIZED_NAME)!!,
                usageCount = (record.getValue(TAGS.USAGE_COUNT) as? Number)?.toInt() ?: 0,
                createdAt = record.getValue(TAGS.CREATED_AT) ?: Instant.now(),
                createdBy = record.getValue(TAGS.CREATED_BY),
                updatedAt = record.getValue(TAGS.UPDATED_AT) ?: Instant.now(),
                updatedBy = record.getValue(TAGS.UPDATED_BY),
                deletedAt = record.getValue(TAGS.DELETED_AT)
            )
        }
    }

    override fun incrementUsageCount(tagId: Long): Mono<Boolean> {
        return Mono.from(
            dslContext
                .update(TAGS)
                .set(TAGS.USAGE_COUNT, TAGS.USAGE_COUNT.plus(1))
                .set(TAGS.UPDATED_AT, Instant.now())
                .where(TAGS.ID.eq(tagId))
                .and(TAGS.DELETED_AT.isNull)
        ).map { rowsUpdated: Any ->
            val success = when (rowsUpdated) {
                is Long -> rowsUpdated > 0L
                is Int -> rowsUpdated > 0
                else -> rowsUpdated.toString().toLongOrNull()?.let { it > 0L } ?: false
            }
            logger.debug("Increment usage count: tagId=$tagId, success=$success")
            success
        }.defaultIfEmpty(false)
    }

    override fun decrementUsageCount(tagId: Long): Mono<Boolean> {
        return Mono.from(
            dslContext
                .update(TAGS)
                .set(TAGS.USAGE_COUNT, TAGS.USAGE_COUNT.minus(1))
                .set(TAGS.UPDATED_AT, Instant.now())
                .where(TAGS.ID.eq(tagId))
                .and(TAGS.DELETED_AT.isNull)
                .and(TAGS.USAGE_COUNT.gt(0))
        ).map { rowsUpdated: Any ->
            val success = when (rowsUpdated) {
                is Long -> rowsUpdated > 0L
                is Int -> rowsUpdated > 0
                else -> rowsUpdated.toString().toLongOrNull()?.let { it > 0L } ?: false
            }
            logger.debug("Decrement usage count: tagId=$tagId, success=$success")
            success
        }.defaultIfEmpty(false)
    }

    override fun delete(tagId: Long, deletedBy: String): Mono<Boolean> {
        return Mono.from(
            dslContext
                .update(TAGS)
                .set(TAGS.DELETED_AT, Instant.now())
                .set(TAGS.UPDATED_AT, Instant.now())
                .set(TAGS.UPDATED_BY, deletedBy)
                .where(TAGS.ID.eq(tagId))
                .and(TAGS.DELETED_AT.isNull)
        ).map { rowsUpdated: Any ->
            val success = when (rowsUpdated) {
                is Long -> rowsUpdated > 0L
                is Int -> rowsUpdated > 0
                else -> rowsUpdated.toString().toLongOrNull()?.let { it > 0L } ?: false
            }
            logger.debug("Tag deleted: tagId=$tagId, success=$success")
            success
        }.defaultIfEmpty(false)
    }
}
