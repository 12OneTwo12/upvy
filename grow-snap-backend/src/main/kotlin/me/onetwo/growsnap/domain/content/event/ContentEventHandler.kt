package me.onetwo.growsnap.domain.content.event

import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.content.model.ContentInteraction
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 콘텐츠 이벤트 핸들러
 *
 * 콘텐츠 관련 이벤트를 비동기로 처리합니다.
 *
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 */
@Component
class ContentEventHandler(
    private val contentInteractionRepository: ContentInteractionRepository
) {

    private val logger = LoggerFactory.getLogger(ContentEventHandler::class.java)

    /**
     * 콘텐츠 생성 이벤트 처리
     *
     * 새로운 콘텐츠가 생성되면 ContentInteraction 레코드를 초기화합니다.
     * 비동기로 처리되어 메인 트랜잭션에 영향을 주지 않습니다.
     *
     * @param event 콘텐츠 생성 이벤트
     */
    @Async
    @EventListener
    fun handleContentCreated(event: ContentCreatedEvent) {
        logger.info("Handling ContentCreatedEvent: contentId=${event.contentId}")

        val contentInteraction = ContentInteraction(
            contentId = event.contentId,
            likeCount = 0,
            commentCount = 0,
            saveCount = 0,
            shareCount = 0,
            viewCount = 0,
            createdAt = LocalDateTime.now(),
            createdBy = event.creatorId,
            updatedAt = LocalDateTime.now(),
            updatedBy = event.creatorId
        )

        contentInteractionRepository.create(contentInteraction)
            .doOnSuccess {
                logger.info("ContentInteraction created successfully: contentId=${event.contentId}")
            }
            .doOnError { error ->
                logger.error("Failed to create ContentInteraction: contentId=${event.contentId}", error)
            }
            .subscribe()
    }
}
