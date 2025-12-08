package me.onetwo.upvy.domain.content.repository

import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.jooq.generated.tables.ContentMetadata.Companion.CONTENT_METADATA
import me.onetwo.upvy.jooq.generated.tables.Contents.Companion.CONTENTS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 콘텐츠 메타데이터 Repository
 *
 * 콘텐츠와 관련된 메타데이터를 조회합니다.
 *
 * @property dsl JOOQ DSL Context
 */
@Repository
class ContentMetadataRepository(
    private val dsl: DSLContext
) {

    /**
     * 여러 콘텐츠의 메타데이터를 일괄 조회
     *
     * N+1 쿼리 문제를 방지하기 위해 IN 절을 사용하여 한 번에 조회합니다.
     *
     * @param contentIds 조회할 콘텐츠 ID 목록
     * @return 콘텐츠 ID를 키로 하는 (제목, 썸네일 URL) Map을 담은 Mono
     */
    fun findContentInfosByContentIds(contentIds: Set<UUID>): Mono<Map<UUID, Pair<String, String>>> {
        if (contentIds.isEmpty()) {
            return Mono.just(emptyMap())
        }

        return Flux.from(
            dsl
                .select(
                    CONTENTS.ID,
                    CONTENT_METADATA.TITLE,
                    CONTENTS.THUMBNAIL_URL
                )
                .from(CONTENTS)
                .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
                .where(CONTENTS.ID.`in`(contentIds.map { it.toString() }))
                .and(CONTENTS.DELETED_AT.isNull)
        )
            .collectMap(
                { UUID.fromString(it.getValue(CONTENTS.ID)) },
                {
                    Pair(
                        it.getValue(CONTENT_METADATA.TITLE) ?: "",
                        it.getValue(CONTENTS.THUMBNAIL_URL) ?: ""
                    )
                }
            )
    }

    /**
     * 콘텐츠의 카테고리를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 카테고리를 담은 Mono (존재하지 않으면 empty)
     */
    fun findCategoryByContentId(contentId: UUID): Mono<Category> {
        return Mono.from(
            dsl
                .select(CONTENT_METADATA.CATEGORY)
                .from(CONTENT_METADATA)
                .join(CONTENTS).on(CONTENTS.ID.eq(CONTENT_METADATA.CONTENT_ID))
                .where(CONTENT_METADATA.CONTENT_ID.eq(contentId.toString()))
                .and(CONTENTS.DELETED_AT.isNull)
                .and(CONTENT_METADATA.DELETED_AT.isNull)
        )
            .mapNotNull { record ->
                record.getValue(CONTENT_METADATA.CATEGORY)?.let { categoryName ->
                    try {
                        Category.valueOf(categoryName)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            }
    }
}
