package me.onetwo.upvy.domain.content.repository

import me.onetwo.upvy.domain.content.model.ContentPhoto
import me.onetwo.upvy.jooq.generated.tables.references.CONTENT_PHOTOS
import org.jooq.DSLContext
import org.jooq.Record
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.UUID

/**
 * 콘텐츠 사진 레포지토리
 *
 * PHOTO 타입 콘텐츠의 사진 목록을 관리합니다.
 *
 * @property dslContext JOOQ DSL Context
 */
@Repository
class ContentPhotoRepository(
    private val dslContext: DSLContext
) {
    private val logger = LoggerFactory.getLogger(ContentPhotoRepository::class.java)

    /**
     * 사진을 저장합니다.
     *
     * @param photo 저장할 사진
     * @return 저장 성공 여부
     */
    fun save(photo: ContentPhoto): Mono<Void> {
        return Mono.from(dslContext
            .insertInto(CONTENT_PHOTOS)
            .set(CONTENT_PHOTOS.CONTENT_ID, photo.contentId.toString())
            .set(CONTENT_PHOTOS.PHOTO_URL, photo.photoUrl)
            .set(CONTENT_PHOTOS.DISPLAY_ORDER, photo.displayOrder)
            .set(CONTENT_PHOTOS.WIDTH, photo.width)
            .set(CONTENT_PHOTOS.HEIGHT, photo.height)
            .set(CONTENT_PHOTOS.CREATED_AT, photo.createdAt)
            .set(CONTENT_PHOTOS.CREATED_BY, photo.createdBy)
            .set(CONTENT_PHOTOS.UPDATED_AT, photo.updatedAt)
            .set(CONTENT_PHOTOS.UPDATED_BY, photo.updatedBy))
            .doOnSuccess { _: Any? ->
                logger.debug("Content photo saved: contentId=${photo.contentId}, order=${photo.displayOrder}")
            }
            .doOnError { e ->
                logger.error("Failed to save content photo: contentId=${photo.contentId}", e)
            }
            .then()
    }

    /**
     * 콘텐츠의 사진 목록을 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 사진 목록 (display_order 순으로 정렬)
     */
    fun findByContentId(contentId: UUID): Mono<List<ContentPhoto>> {
        return Flux.from(dslContext
            .select(
                CONTENT_PHOTOS.ID,
                CONTENT_PHOTOS.CONTENT_ID,
                CONTENT_PHOTOS.PHOTO_URL,
                CONTENT_PHOTOS.DISPLAY_ORDER,
                CONTENT_PHOTOS.WIDTH,
                CONTENT_PHOTOS.HEIGHT,
                CONTENT_PHOTOS.CREATED_AT,
                CONTENT_PHOTOS.CREATED_BY,
                CONTENT_PHOTOS.UPDATED_AT,
                CONTENT_PHOTOS.UPDATED_BY,
                CONTENT_PHOTOS.DELETED_AT
            )
            .from(CONTENT_PHOTOS)
            .where(CONTENT_PHOTOS.CONTENT_ID.eq(contentId.toString()))
            .and(CONTENT_PHOTOS.DELETED_AT.isNull)
            .orderBy(CONTENT_PHOTOS.DISPLAY_ORDER.asc()))
            .map { mapRecordToContentPhoto(it) }
            .collectList()
    }

    /**
     * 콘텐츠의 모든 사진을 삭제합니다 (Soft Delete).
     *
     * @param contentId 콘텐츠 ID
     * @param deletedBy 삭제한 사용자 ID
     * @return 삭제된 행 수
     */
    fun deleteByContentId(contentId: UUID, deletedBy: String): Mono<Int> {
        return Mono.from(dslContext
            .update(CONTENT_PHOTOS)
            .set(CONTENT_PHOTOS.DELETED_AT, Instant.now())
            .set(CONTENT_PHOTOS.UPDATED_BY, deletedBy)
            .where(CONTENT_PHOTOS.CONTENT_ID.eq(contentId.toString()))
            .and(CONTENT_PHOTOS.DELETED_AT.isNull))
            .map { rowsAffected: Any ->
                val count: Int = when (rowsAffected) {
                    is Long -> rowsAffected.toInt()
                    is Int -> rowsAffected
                    else -> rowsAffected.toString().toIntOrNull() ?: 0
                }
                count
            }
            .defaultIfEmpty(0)
    }

    /**
     * 여러 콘텐츠의 사진 목록을 일괄 조회합니다 (N+1 방지).
     *
     * @param contentIds 콘텐츠 ID 목록
     * @return 콘텐츠 ID별로 그룹화된 사진 목록 Map
     */
    fun findByContentIds(contentIds: List<UUID>): Mono<Map<UUID, List<ContentPhoto>>> {
        if (contentIds.isEmpty()) {
            return Mono.just(emptyMap())
        }

        return Flux.from(dslContext
            .select(
                CONTENT_PHOTOS.ID,
                CONTENT_PHOTOS.CONTENT_ID,
                CONTENT_PHOTOS.PHOTO_URL,
                CONTENT_PHOTOS.DISPLAY_ORDER,
                CONTENT_PHOTOS.WIDTH,
                CONTENT_PHOTOS.HEIGHT,
                CONTENT_PHOTOS.CREATED_AT,
                CONTENT_PHOTOS.CREATED_BY,
                CONTENT_PHOTOS.UPDATED_AT,
                CONTENT_PHOTOS.UPDATED_BY,
                CONTENT_PHOTOS.DELETED_AT
            )
            .from(CONTENT_PHOTOS)
            .where(CONTENT_PHOTOS.CONTENT_ID.`in`(contentIds.map { it.toString() }))
            .and(CONTENT_PHOTOS.DELETED_AT.isNull)
            .orderBy(CONTENT_PHOTOS.DISPLAY_ORDER.asc()))
            .collectList()
            .map { records ->
                records
                    .groupBy { record -> UUID.fromString(record.getValue(CONTENT_PHOTOS.CONTENT_ID)) }
                    .mapValues { (_, recs) ->
                        recs.map { mapRecordToContentPhoto(it) }
                    }
            }
    }

    /**
     * JOOQ Record를 ContentPhoto 엔티티로 변환합니다.
     *
     * @param record JOOQ Record
     * @return ContentPhoto 엔티티
     */
    private fun mapRecordToContentPhoto(record: Record): ContentPhoto {
        return ContentPhoto(
            id = record.getValue(CONTENT_PHOTOS.ID),
            contentId = UUID.fromString(record.getValue(CONTENT_PHOTOS.CONTENT_ID)),
            photoUrl = record.getValue(CONTENT_PHOTOS.PHOTO_URL)!!,
            displayOrder = record.getValue(CONTENT_PHOTOS.DISPLAY_ORDER)!!,
            width = record.getValue(CONTENT_PHOTOS.WIDTH)!!,
            height = record.getValue(CONTENT_PHOTOS.HEIGHT)!!,
            createdAt = record.getValue(CONTENT_PHOTOS.CREATED_AT)!!,
            createdBy = record.getValue(CONTENT_PHOTOS.CREATED_BY),
            updatedAt = record.getValue(CONTENT_PHOTOS.UPDATED_AT)!!,
            updatedBy = record.getValue(CONTENT_PHOTOS.UPDATED_BY),
            deletedAt = record.getValue(CONTENT_PHOTOS.DELETED_AT)
        )
    }
}
