package me.onetwo.upvy.domain.interaction.service

import me.onetwo.upvy.domain.analytics.dto.InteractionType
import me.onetwo.upvy.domain.analytics.event.UserInteractionEvent
import me.onetwo.upvy.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.upvy.domain.analytics.service.ContentInteractionService
import me.onetwo.upvy.domain.interaction.dto.ShareLinkResponse
import me.onetwo.upvy.domain.interaction.dto.ShareResponse
import me.onetwo.upvy.infrastructure.event.ReactiveEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 공유 서비스 구현체
 *
 * ## 처리 흐름
 * 1. content_interactions.share_count 증가 (메인 체인, 즉시 반영)
 * 2. UserInteractionEvent 발행 (협업 필터링용, 비동기)
 * 3. 응답 반환
 *
 * @property contentInteractionService 콘텐츠 인터랙션 서비스
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 * @property eventPublisher Reactive 이벤트 발행자
 */
@Service
@Transactional(readOnly = true)
class ShareServiceImpl(
    private val contentInteractionService: ContentInteractionService,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val eventPublisher: ReactiveEventPublisher
) : ShareService {

    /**
     * 콘텐츠 공유
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 공유 응답
     */
    @Transactional
    override fun shareContent(userId: UUID, contentId: UUID): Mono<ShareResponse> {
        logger.debug("Sharing content: userId={}, contentId={}", userId, contentId)

        // 카운트 증가를 메인 체인에 포함
        return contentInteractionService.incrementShareCount(contentId)
            .doOnSuccess {
                logger.debug("Publishing UserInteractionEvent: userId={}, contentId={}", userId, contentId)
                // 협업 필터링만 이벤트로 처리 (실패해도 OK)
                eventPublisher.publish(
                    UserInteractionEvent(
                        userId = userId,
                        contentId = contentId,
                        interactionType = InteractionType.SHARE
                    )
                )
            }
            .then(getShareResponse(contentId))
            .doOnSuccess { logger.debug("Content shared successfully: userId={}, contentId={}", userId, contentId) }
            .doOnError { error ->
                logger.error("Failed to share content: userId={}, contentId={}", userId, contentId, error)
            }
    }

    /**
     * 공유 응답 생성
     *
     * @param contentId 콘텐츠 ID
     * @return 공유 응답
     */
    private fun getShareResponse(contentId: UUID): Mono<ShareResponse> {
        return contentInteractionRepository.getShareCount(contentId)
            .map { shareCount ->
                ShareResponse(
                    contentId = contentId.toString(),
                    shareCount = shareCount
                )
            }
    }

    @Transactional(readOnly = true)
    override fun getShareLink(contentId: UUID): Mono<ShareLinkResponse> {
        logger.debug("Getting share link: contentId={}", contentId)

        val shareUrl = "https://upvy.com/watch/$contentId"

        return Mono.just(
            ShareLinkResponse(
                contentId = contentId.toString(),
                shareUrl = shareUrl
            )
        ).doOnSuccess { logger.debug("Share link generated: url={}", shareUrl) }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ShareServiceImpl::class.java)
    }
}
