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

        return Flux.fromIterable(tagNames)
            .flatMap { tagName ->
                findOrCreateTag(tagName, userId)
                    .flatMap { tag ->
                        contentTagRepository.existsByContentIdAndTagId(contentId, tag.id!!)
                            .flatMap { exists ->
                                if (exists) {
                                    logger.debug("Tag already attached: contentId=$contentId, tagId=${tag.id}")
                                    Mono.just(tag)
                                } else {
                                    val contentTag = ContentTag(
                                        contentId = contentId,
                                        tagId = tag.id,
                                        createdAt = Instant.now(),
                                        createdBy = userId,
                                        updatedAt = Instant.now(),
                                        updatedBy = userId
                                    )

                                    contentTagRepository.save(contentTag)
                                        .flatMap {
                                            tagRepository.incrementUsageCount(tag.id)
                                                .thenReturn(tag)
                                        }
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

                contentTagRepository.deleteByContentId(contentId, userId)
                    .flatMap { deletedCount ->
                        Flux.fromIterable(tagIds)
                            .flatMap { tagId ->
                                tagRepository.decrementUsageCount(tagId)
                            }
                            .then(Mono.just(deletedCount))
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
