package me.onetwo.upvy.crawler.backoffice.service

import kotlinx.coroutines.runBlocking
import me.onetwo.upvy.crawler.backoffice.domain.PendingContent
import me.onetwo.upvy.crawler.backoffice.repository.PendingContentRepository
import me.onetwo.upvy.crawler.client.LlmClient
import me.onetwo.upvy.crawler.domain.ContentLanguage
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
 * 또한 LLM을 통해 퀴즈를 자동 생성하여 함께 저장합니다.
 */
@Service
class ContentPublishService(
    private val pendingContentRepository: PendingContentRepository,
    private val publishedContentRepository: PublishedContentRepository,
    private val publishedContentMetadataRepository: PublishedContentMetadataRepository,
    private val publishedContentInteractionRepository: PublishedContentInteractionRepository,
    private val tagRepository: TagRepository,
    private val contentTagRepository: ContentTagRepository,
    private val quizRepository: QuizRepository,
    private val quizOptionRepository: QuizOptionRepository,
    private val llmClient: LlmClient,
    @Value("\${crawler.system-user-id:00000000-0000-0000-0000-000000000001}")
    private val systemUserId: String,
    @Value("\${s3.bucket:upvy-ai-media-bucket}")
    private val s3Bucket: String,
    @Value("\${s3.region:ap-northeast-2}")
    private val s3Region: String
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ContentPublishService::class.java)
        private val objectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
    }

    /**
     * 콘텐츠 게시
     *
     * pending_contents의 콘텐츠를 백엔드 DB의 contents, content_metadata,
     * content_interactions 테이블에 INSERT합니다.
     *
     * @param pendingContentId 승인 대기 콘텐츠 ID
     * @param createQuiz 퀴즈 생성 여부 (기본값: true)
     * @return 생성된 contents.id (UUID)
     */
    @Transactional
    fun publishContent(pendingContentId: Long, createQuiz: Boolean = true): String {
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

        // 5. 퀴즈 생성 및 발행 (createQuiz가 true일 때만)
        if (createQuiz) {
            try {
                createQuizForContent(contentId, pendingContent)
                logger.info("퀴즈 생성 완료: contentId={}", contentId)
            } catch (e: Exception) {
                logger.error("퀴즈 생성 실패 (콘텐츠 발행은 계속): contentId={}", contentId, e)
                // 퀴즈 생성 실패해도 콘텐츠 발행은 계속 진행
            }
        } else {
            logger.info("퀴즈 생성 스킵 (관리자 선택): contentId={}", contentId)
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

    /**
     * 콘텐츠에 퀴즈 생성
     *
     * n8n에서 생성된 quiz가 있으면 그것을 사용하고,
     * 없으면 LLM을 사용하여 콘텐츠 설명 기반 퀴즈를 생성합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param pendingContent 승인 대기 콘텐츠
     * @throws Exception 퀴즈 생성 또는 INSERT 실패 시
     */
    private fun createQuizForContent(contentId: String, pendingContent: PendingContent) {
        val now = Instant.now()
        val quizId = UUID.randomUUID().toString()

        // n8n에서 생성된 quiz가 있는지 확인
        val existingQuiz = pendingContent.quiz
        if (!existingQuiz.isNullOrBlank()) {
            logger.debug("n8n 생성 퀴즈 사용 시도: contentId={}", contentId)
            if (saveQuizFromN8n(contentId, quizId, existingQuiz, now)) {
                return  // n8n 퀴즈 저장 성공
            }
            // n8n 퀴즈 파싱 실패 - LLM으로 대체
            logger.warn("n8n 퀴즈 파싱 실패, LLM으로 대체: contentId={}", contentId)
        }

        // description이 없으면 퀴즈 생성 불가
        val description = pendingContent.description
        if (description.isNullOrBlank()) {
            logger.warn("퀴즈 생성 스킵: description이 없음, contentId={}", contentId)
            return
        }

        logger.debug("LLM 퀴즈 생성 시작: contentId={}, title={}", contentId, pendingContent.title)

        // LLM으로 퀴즈 생성
        val contentLanguage = ContentLanguage.fromCode(pendingContent.language) ?: ContentLanguage.KO
        val quizData = runBlocking {
            llmClient.generateQuizFromDescription(
                description = description,
                title = pendingContent.title,
                contentLanguage = contentLanguage,
                difficulty = pendingContent.difficulty
            )
        }

        logger.debug("LLM 퀴즈 생성 완료: question={}, options count={}",
            quizData.question.take(50), quizData.options.size)

        // quizzes 테이블 INSERT
        val quiz = Quiz(
            id = quizId,
            contentId = contentId,
            question = quizData.question,
            allowMultipleAnswers = quizData.allowMultipleAnswers,
            createdAt = now,
            createdBy = systemUserId,
            updatedAt = now,
            updatedBy = systemUserId
        )
        quizRepository.save(quiz)
        logger.debug("quizzes INSERT 완료: quizId={}", quizId)

        // quiz_options 테이블 INSERT (정답 위치 랜덤화를 위해 셔플)
        quizData.options.shuffled().forEachIndexed { index, option ->
            val quizOption = QuizOption(
                id = UUID.randomUUID().toString(),
                quizId = quizId,
                optionText = option.optionText,
                isCorrect = option.isCorrect,
                displayOrder = index + 1,  // 1부터 시작
                createdAt = now,
                createdBy = systemUserId,
                updatedAt = now,
                updatedBy = systemUserId
            )
            quizOptionRepository.save(quizOption)
        }
        logger.debug("quiz_options INSERT 완료: count={}", quizData.options.size)
    }

    /**
     * n8n에서 생성된 퀴즈 JSON을 파싱하여 저장
     *
     * n8n quiz format:
     * {"question":"...", "options":[{"text":"...", "isCorrect":true}, ...]}
     *
     * @return 저장 성공 시 true, 파싱 실패 시 false
     */
    private fun saveQuizFromN8n(contentId: String, quizId: String, quizJson: String, now: Instant): Boolean {
        return try {
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val quizNode = mapper.readTree(quizJson)

            val question = quizNode.get("question")?.asText()
                ?: throw IllegalArgumentException("quiz JSON에 question이 없습니다")
            val optionsNode = quizNode.get("options")
                ?: throw IllegalArgumentException("quiz JSON에 options가 없습니다")

            // quizzes 테이블 INSERT
            val quiz = Quiz(
                id = quizId,
                contentId = contentId,
                question = question,
                allowMultipleAnswers = false,
                createdAt = now,
                createdBy = systemUserId,
                updatedAt = now,
                updatedBy = systemUserId
            )
            quizRepository.save(quiz)
            logger.debug("n8n quizzes INSERT 완료: quizId={}", quizId)

            // quiz_options 테이블 INSERT
            val options = optionsNode.toList().shuffled()  // 정답 위치 랜덤화
            options.forEachIndexed { index, optionNode ->
                val optionText = optionNode.get("text")?.asText() ?: return@forEachIndexed
                val isCorrect = optionNode.get("isCorrect")?.asBoolean() ?: false

                val quizOption = QuizOption(
                    id = UUID.randomUUID().toString(),
                    quizId = quizId,
                    optionText = optionText,
                    isCorrect = isCorrect,
                    displayOrder = index + 1,
                    createdAt = now,
                    createdBy = systemUserId,
                    updatedAt = now,
                    updatedBy = systemUserId
                )
                quizOptionRepository.save(quizOption)
            }
            logger.debug("n8n quiz_options INSERT 완료: count={}", options.size)
            true
        } catch (e: Exception) {
            logger.error("n8n 퀴즈 파싱 실패, LLM으로 대체 시도: {}", e.message)
            false
        }
    }
}
