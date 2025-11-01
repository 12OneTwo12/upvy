package me.onetwo.growsnap.domain.interaction.service

import me.onetwo.growsnap.domain.analytics.dto.InteractionEventRequest
import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.analytics.service.AnalyticsService
import me.onetwo.growsnap.domain.interaction.dto.ShareLinkResponse
import me.onetwo.growsnap.domain.interaction.dto.ShareResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 공유 서비스 구현체
 *
 * 콘텐츠 공유 비즈니스 로직을 처리합니다.
 *
 * ### 처리 흐름
 * 1. AnalyticsService를 통한 이벤트 발행
 *    - 카운터 증가 (content_interactions 테이블)
 *    - Spring Event 발행 (UserInteractionEvent)
 *    - user_content_interactions 테이블 저장 (협업 필터링용)
 *
 * @property analyticsService Analytics 서비스 (이벤트 발행)
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 */
@Service
class ShareServiceImpl(
    private val analyticsService: AnalyticsService,
    private val contentInteractionRepository: ContentInteractionRepository
) : ShareService {

    /**
     * 콘텐츠 공유
     *
     * ### 처리 흐름
     * 1. AnalyticsService.trackInteractionEvent(SHARE) 호출
     *    - content_interactions의 share_count 증가
     *    - UserInteractionEvent 발행
     *    - UserInteractionEventListener가 user_content_interactions 저장 (협업 필터링용)
     * 2. 공유 수 조회 및 응답 반환
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 공유 응답
     */
    @Transactional
    override fun shareContent(userId: UUID, contentId: UUID): Mono<ShareResponse> {
        logger.debug("Sharing content: userId={}, contentId={}", userId, contentId)

        // AnalyticsService로 이벤트 발행 (카운터 증가 + user_content_interactions 저장)
        return analyticsService.trackInteractionEvent(
            userId,
            InteractionEventRequest(
                contentId = contentId,
                interactionType = InteractionType.SHARE
            )
        )
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

        val shareUrl = "https://growsnap.com/watch/$contentId"

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
