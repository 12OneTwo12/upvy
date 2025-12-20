package me.onetwo.upvy.crawler.backoffice.service

import me.onetwo.upvy.crawler.backoffice.domain.PendingContent
import me.onetwo.upvy.crawler.backoffice.repository.PendingContentRepository
import me.onetwo.upvy.crawler.domain.content.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * 콘텐츠 게시 서비스
 *
 * 승인된 콘텐츠를 백엔드 contents/content_metadata/content_interactions 테이블에 INSERT합니다.
 */
@Service
class ContentPublishService(
    private val pendingContentRepository: PendingContentRepository,
    private val publishedContentRepository: PublishedContentRepository,
    private val publishedContentMetadataRepository: PublishedContentMetadataRepository,
    private val publishedContentInteractionRepository: PublishedContentInteractionRepository,
    private val tagRepository: TagRepository,
    private val contentTagRepository: ContentTagRepository,
    @Value("\${crawler.system-user-id:00000000-0000-0000-0000-000000000001}")
    private val systemUserId: String,
    @Value("\${s3.bucket:upvy-ai-media}")
    private val s3Bucket: String,
    @Value("\${s3.region:ap-northeast-2}")
    private val s3Region: String
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ContentPublishService::class.java)
    }

    /**
     * 콘텐츠 게시
     *
     * pending_contents의 콘텐츠를 백엔드 DB의 contents, content_metadata,
     * content_interactions 테이블에 INSERT합니다.
     *
     * @param pendingContentId 승인 대기 콘텐츠 ID
     * @return 생성된 contents.id (UUID)
     */
    @Transactional
    fun publishContent(pendingContentId: Long): String {
        val pendingContent = pendingContentRepository.findById(pendingContentId)
            .orElseThrow { IllegalArgumentException("콘텐츠를 찾을 수 없습니다: id=$pendingContentId") }

        logger.info("콘텐츠 게시 시작: pendingContentId={}, title={}", pendingContentId, pendingContent.title)

        val contentId = UUID.randomUUID().toString()
        val now = Instant.now()

        // 1. contents 테이블 INSERT
        val content = createPublishedContent(contentId, pendingContent, now)
        publishedContentRepository.save(content)
        logger.debug("contents INSERT 완료: contentId={}", contentId)

        // 2. content_metadata 테이블 INSERT
        val metadata = createPublishedContentMetadata(contentId, pendingContent, now)
        publishedContentMetadataRepository.save(metadata)
        logger.debug("content_metadata INSERT 완료: contentId={}", contentId)

        // 3. content_interactions 테이블 INSERT (초기값 0)
        val interaction = createPublishedContentInteraction(contentId, now)
        publishedContentInteractionRepository.save(interaction)
        logger.debug("content_interactions INSERT 완료: contentId={}", contentId)

        // 4. tags 및 content_tags 테이블 INSERT
        val tagsList = pendingContent.getTagsList()
        if (tagsList.isNotEmpty()) {
            saveTagsAndRelations(contentId, tagsList, now)
            logger.debug("tags 및 content_tags INSERT 완료: contentId={}, tags={}", contentId, tagsList)
        }

        logger.info(
            "콘텐츠 게시 완료: pendingContentId={}, publishedContentId={}, tags={}",
            pendingContentId, contentId, tagsList
        )

        return contentId
    }

    /**
     * PublishedContent 엔티티 생성
     */
    private fun createPublishedContent(
        contentId: String,
        pendingContent: PendingContent,
        now: Instant
    ): PublishedContent {
        val videoUrl = buildS3PublicUrl(pendingContent.videoS3Key)
        val thumbnailUrl = pendingContent.thumbnailS3Key?.let { buildS3PublicUrl(it) } ?: videoUrl

        return PublishedContent(
            id = contentId,
            creatorId = systemUserId,
            contentType = ContentType.VIDEO,
            url = videoUrl,
            thumbnailUrl = thumbnailUrl,
            duration = pendingContent.durationSeconds,
            width = pendingContent.width,
            height = pendingContent.height,
            status = ContentStatus.PUBLISHED,
            createdAt = now,
            createdBy = systemUserId,
            updatedAt = now,
            updatedBy = systemUserId
        )
    }

    /**
     * PublishedContentMetadata 엔티티 생성
     */
    private fun createPublishedContentMetadata(
        contentId: String,
        pendingContent: PendingContent,
        now: Instant
    ): PublishedContentMetadata {
        return PublishedContentMetadata(
            contentId = contentId,
            title = pendingContent.title,
            description = pendingContent.description,
            category = pendingContent.category.name,
            tags = pendingContent.tags,
            difficultyLevel = pendingContent.difficulty?.name,
            language = pendingContent.language,  // 콘텐츠 언어
            createdAt = now,
            createdBy = systemUserId,
            updatedAt = now,
            updatedBy = systemUserId
        )
    }

    /**
     * PublishedContentInteraction 엔티티 생성 (초기값 0)
     */
    private fun createPublishedContentInteraction(
        contentId: String,
        now: Instant
    ): PublishedContentInteraction {
        return PublishedContentInteraction(
            contentId = contentId,
            likeCount = 0,
            commentCount = 0,
            saveCount = 0,
            shareCount = 0,
            viewCount = 0,
            createdAt = now,
            createdBy = systemUserId,
            updatedAt = now,
            updatedBy = systemUserId
        )
    }

    /**
     * 태그 및 콘텐츠-태그 관계 저장
     *
     * @param contentId 콘텐츠 ID
     * @param tagNames 태그 이름 목록
     * @param now 현재 시각
     */
    private fun saveTagsAndRelations(contentId: String, tagNames: List<String>, now: Instant) {
        tagNames.forEach { tagName ->
            // 1. 태그 찾거나 생성
            val tag = findOrCreateTag(tagName, now)

            // 2. content_tags 관계가 이미 존재하는지 확인
            val alreadyExists = contentTagRepository.existsByContentIdAndTagIdAndDeletedAtIsNull(
                contentId,
                tag.id!!
            )

            if (!alreadyExists) {
                // 3. content_tags 관계 생성
                val contentTag = ContentTag(
                    contentId = contentId,
                    tagId = tag.id,
                    createdAt = now,
                    createdBy = systemUserId,
                    updatedAt = now,
                    updatedBy = systemUserId
                )
                contentTagRepository.save(contentTag)

                // 4. tags.usage_count 증가
                tagRepository.incrementUsageCount(tag.id)
            }
        }
    }

    /**
     * 태그를 찾거나 생성합니다.
     *
     * @param tagName 태그 이름
     * @param now 현재 시각
     * @return 태그 엔티티
     */
    private fun findOrCreateTag(tagName: String, now: Instant): Tag {
        val trimmedName = tagName.trim()
        val normalizedName = Tag.normalizeTagName(trimmedName)

        // 기존 태그 조회
        return tagRepository.findByNormalizedNameAndDeletedAtIsNull(normalizedName)
            .getOrNull()
            ?: run {
                // 새 태그 생성
                val newTag = Tag(
                    name = trimmedName,
                    normalizedName = normalizedName,
                    usageCount = 0,
                    createdAt = now,
                    createdBy = systemUserId,
                    updatedAt = now,
                    updatedBy = systemUserId
                )
                tagRepository.save(newTag)
            }
    }

    /**
     * S3 Public URL 생성
     *
     * @param s3Key S3 객체 키
     * @return Public URL (https://{bucket}.s3.{region}.amazonaws.com/{key})
     */
    private fun buildS3PublicUrl(s3Key: String): String {
        return "https://$s3Bucket.s3.$s3Region.amazonaws.com/$s3Key"
    }
}
