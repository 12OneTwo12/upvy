package me.onetwo.upvy.domain.tag.service

import me.onetwo.upvy.domain.tag.model.ContentTag
import me.onetwo.upvy.domain.tag.model.ContentTagsProjection
import me.onetwo.upvy.domain.tag.model.Tag
import me.onetwo.upvy.domain.tag.repository.ContentTagRepository
import me.onetwo.upvy.domain.tag.repository.TagRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 태그 서비스 구현체
 *
 * 태그 생성, 콘텐츠-태그 연결, 태그 인기도 관리 등 비즈니스 로직을 처리합니다.
 *
 * @property tagRepository 태그 레포지토리
 * @property contentTagRepository 콘텐츠-태그 관계 레포지토리
 */
@Service
class TagServiceImpl(
    private val tagRepository: TagRepository,
    private val contentTagRepository: ContentTagRepository
) : TagService {

    private val logger = LoggerFactory.getLogger(TagServiceImpl::class.java)

    override fun attachTagsToContent(
        contentId: UUID,
        tagNames: List<String>,
        userId: String
    ): Flux<Tag> {
        if (tagNames.isEmpty()) {
            return Flux.empty()
        }

        val now = Instant.now()

        // Step 1: Normalize all tag names
        val normalizedMap = tagNames.associateBy { normalizeTagName(it.trim()) }
        val normalizedNames = normalizedMap.keys.toList()

        // Step 2: Bulk lookup existing tags by normalized names
        return tagRepository.findByNormalizedNames(normalizedNames)
            .collectList()
            .flatMapMany { existingTags ->
                val existingNormalizedNames = existingTags.map { it.normalizedName }.toSet()
                val missingNormalizedNames = normalizedNames.filter { it !in existingNormalizedNames }

                // Step 3: Create new tags for missing ones (bulk insert)
                val newTagsMono = if (missingNormalizedNames.isNotEmpty()) {
                    val newTags = missingNormalizedNames.map { normalizedName ->
                        Tag(
                            name = normalizedMap[normalizedName]!!.trim(),
                            normalizedName = normalizedName,
                            usageCount = 0,
                            createdAt = now,
                            createdBy = userId,
                            updatedAt = now,
                            updatedBy = userId
                        )
                    }
                    tagRepository.saveAll(newTags).collectList()
                } else {
                    Mono.just(emptyList())
                }

                newTagsMono.flatMapMany { newTags ->
                    val allTags = existingTags + newTags
                    val allTagIds = allTags.mapNotNull { it.id }

                    // Step 4: Bulk check which content-tag relationships already exist
                    contentTagRepository.findExistingTagIds(contentId, allTagIds)
                        .collectList()
                        .flatMapMany { existingTagIds ->
                            val existingTagIdSet = existingTagIds.toSet()

                            // Step 5: Filter out existing relationships
                            val tagsToAttach = allTags.filter { it.id!! !in existingTagIdSet }

                            if (tagsToAttach.isEmpty()) {
                                logger.debug("All tags already attached: contentId=$contentId")
                                return@flatMapMany Flux.fromIterable(allTags)
                            }

                            // Step 6: Bulk insert new content-tag relationships
                            val contentTags = tagsToAttach.map { tag ->
                                ContentTag(
                                    contentId = contentId,
                                    tagId = tag.id!!,
                                    createdAt = now,
                                    createdBy = userId,
                                    updatedAt = now,
                                    updatedBy = userId
                                )
                            }

                            val newTagIds = tagsToAttach.mapNotNull { it.id }

                            contentTagRepository.saveAll(contentTags)
                                .then(
                                    // Step 7: Bulk increment usage counts
                                    tagRepository.incrementUsageCountBatch(newTagIds)
                                )
                                .thenMany(Flux.fromIterable(allTags))
                                .doOnComplete {
                                    logger.debug("Tags attached: contentId=$contentId, newTagsCount=${tagsToAttach.size}")
                                }
                        }
                }
            }
    }

    override fun detachTagsFromContent(contentId: UUID, userId: String): Mono<Int> {
        return contentTagRepository.findTagIdsByContentId(contentId)
            .collectList()
            .flatMap { tagIds ->
                if (tagIds.isEmpty()) {
                    return@flatMap Mono.just(0)
                }

                // Delete all content-tag relationships and bulk decrement usage counts
                contentTagRepository.deleteByContentId(contentId, userId)
                    .flatMap { deletedCount ->
                        tagRepository.decrementUsageCountBatch(tagIds)
                            .thenReturn(deletedCount)
                            .doOnSuccess {
                                logger.debug("Tags detached: contentId=$contentId, count=$deletedCount")
                            }
                    }
            }
    }

    override fun getTagsByContentId(contentId: UUID): Flux<Tag> {
        return contentTagRepository.findTagIdsByContentId(contentId)
            .collectList()
            .flatMapMany { tagIds ->
                if (tagIds.isEmpty()) {
                    Flux.empty()
                } else {
                    tagRepository.findByIds(tagIds)
                }
            }
    }

    override fun getTagsByContentIds(contentIds: List<UUID>): Flux<ContentTagsProjection> {
        if (contentIds.isEmpty()) {
            return Flux.empty()
        }

        return tagRepository.findByContentIds(contentIds)
    }

    override fun getPopularTags(limit: Int): Flux<Tag> {
        return tagRepository.findPopularTags(limit)
    }

    override fun normalizeTagName(tagName: String): String {
        return tagName
            .trim()
            .lowercase()
            .removePrefix("#")
    }

    override fun findOrCreateTag(tagName: String, userId: String): Mono<Tag> {
        val trimmedName = tagName.trim()
        val normalizedName = normalizeTagName(trimmedName)

        return tagRepository.findByNormalizedName(normalizedName)
            .switchIfEmpty(
                Mono.defer {
                    val newTag = Tag(
                        name = trimmedName,
                        normalizedName = normalizedName,
                        usageCount = 0,
                        createdAt = Instant.now(),
                        createdBy = userId,
                        updatedAt = Instant.now(),
                        updatedBy = userId
                    )

                    tagRepository.save(newTag)
                        .doOnSuccess {
                            logger.info("New tag created: name=$trimmedName, normalizedName=$normalizedName")
                        }
                }
            )
    }
}
