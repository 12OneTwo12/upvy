package me.onetwo.growsnap.domain.analytics.service

import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.content.model.ContentInteraction
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

/**
 * 콘텐츠 인터랙션 서비스 구현체
 *
 * 콘텐츠 인터랙션 생성 및 관리를 담당합니다.
 *
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 */
@Service
class ContentInteractionServiceImpl(
    private val contentInteractionRepository: ContentInteractionRepository
) : ContentInteractionService {

    private val logger = LoggerFactory.getLogger(ContentInteractionServiceImpl::class.java)

    /**
     * 콘텐츠 인터랙션 생성
     *
     * 새로운 콘텐츠가 생성되면 인터랙션 레코드를 초기화합니다.
     * 모든 카운터는 0으로 시작합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param creatorId 생성자 ID
     */
    @Transactional
    override fun createContentInteraction(contentId: UUID, creatorId: UUID) {
        logger.info("Creating ContentInteraction: contentId=$contentId, creatorId=$creatorId")

        val contentInteraction = ContentInteraction(
            contentId = contentId,
            likeCount = 0,
            commentCount = 0,
            saveCount = 0,
            shareCount = 0,
            viewCount = 0,
            createdAt = LocalDateTime.now(),
            createdBy = creatorId.toString(),
            updatedAt = LocalDateTime.now(),
            updatedBy = creatorId.toString()
        )

        contentInteractionRepository.create(contentInteraction)
            .doOnSuccess {
                logger.info("ContentInteraction created successfully: contentId=$contentId")
            }
            .doOnError { error ->
                logger.error("Failed to create ContentInteraction: contentId=$contentId", error)
            }
            .block()
    }

    @Transactional
    override fun incrementLikeCount(contentId: UUID): Mono<Void> {
        return contentInteractionRepository.incrementLikeCount(contentId)
    }

    @Transactional
    override fun decrementLikeCount(contentId: UUID): Mono<Void> {
        return contentInteractionRepository.decrementLikeCount(contentId)
    }

    @Transactional
    override fun incrementSaveCount(contentId: UUID): Mono<Void> {
        return contentInteractionRepository.incrementSaveCount(contentId)
    }

    @Transactional
    override fun decrementSaveCount(contentId: UUID): Mono<Void> {
        return contentInteractionRepository.decrementSaveCount(contentId)
    }

    @Transactional
    override fun incrementCommentCount(contentId: UUID): Mono<Void> {
        return contentInteractionRepository.incrementCommentCount(contentId)
    }

    @Transactional
    override fun decrementCommentCount(contentId: UUID): Mono<Void> {
        return contentInteractionRepository.decrementCommentCount(contentId)
    }
}
