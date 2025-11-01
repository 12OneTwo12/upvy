package me.onetwo.growsnap.domain.content.event

import me.onetwo.growsnap.domain.analytics.service.ContentInteractionService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * 콘텐츠 이벤트 핸들러
 *
 * 콘텐츠 관련 이벤트를 비동기로 처리합니다.
 *
 * @property contentInteractionService 콘텐츠 인터랙션 서비스
 */
@Component
class ContentEventHandler(
    private val contentInteractionService: ContentInteractionService
) {

    private val logger = LoggerFactory.getLogger(ContentEventHandler::class.java)

    /**
     * 콘텐츠 생성 이벤트 처리
     *
     * 새로운 콘텐츠가 생성되면 ContentInteraction 레코드를 초기화합니다.
     * 비동기로 처리되어 메인 요청에 영향을 주지 않습니다.
     *
     * @param event 콘텐츠 생성 이벤트
     */
    @Async
    @EventListener
    fun handleContentCreated(event: ContentCreatedEvent) {
        logger.info("Handling ContentCreatedEvent: contentId=${event.contentId}")

        contentInteractionService.createContentInteraction(event.contentId, event.creatorId)
    }
}
