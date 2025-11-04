package me.onetwo.growsnap.domain.analytics.service

import me.onetwo.growsnap.domain.analytics.dto.InteractionEventRequest
import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.dto.ViewEventRequest
import me.onetwo.growsnap.domain.analytics.event.UserInteractionEvent
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.analytics.repository.UserViewHistoryRepository
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Analytics 서비스 구현체
 *
 * 사용자 행동 추적 (시청 기록, 인터랙션 카운트)
 * - 시청 이벤트: user_view_history 저장 및 view_count 증가
 * - 인터랙션 이벤트: content_interactions 카운트 증가 및 UserInteractionEvent 발행
 *
 * @property userViewHistoryRepository 사용자 시청 기록 레포지토리
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 * @property eventPublisher Reactive 이벤트 발행자
 */
@Service
@Transactional(readOnly = true)
class AnalyticsServiceImpl(
    private val userViewHistoryRepository: UserViewHistoryRepository,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val eventPublisher: ReactiveEventPublisher
) : AnalyticsService {

    /**
     * 시청 이벤트 추적
     *
     * @param userId 사용자 ID
     * @param request 시청 이벤트 요청
     * @return 처리 완료 신호
     */
    @Transactional
    override fun trackViewEvent(userId: UUID, request: ViewEventRequest): Mono<Void> {
        logger.debug(
            "Tracking view event: userId={}, contentId={}, duration={}, completion={}, skipped={}",
            userId,
            request.contentId,
            request.watchedDuration,
            request.completionRate,
            request.skipped
        )

        // 1. 시청 기록 저장
        val saveHistory = userViewHistoryRepository.save(
            userId,
            request.contentId!!,
            request.watchedDuration!!,
            request.completionRate!!
        )

        // 2. 스킵이 아닌 경우 view_count 증가
        return if (request.skipped == false) {
            saveHistory.then(
                contentInteractionRepository.incrementViewCount(request.contentId)
            )
        } else {
            saveHistory
        }
    }

    /**
     * 인터랙션 이벤트 추적
     *
     * content_interactions 카운터 증가 및 UserInteractionEvent 발행
     * - LIKE, SAVE, SHARE: 카운트 증가 + 이벤트 발행 (협업 필터링용)
     * - COMMENT: 카운트 증가만
     *
     * @param userId 사용자 ID
     * @param request 인터랙션 이벤트 요청
     * @return 처리 완료 신호
     */
    @Transactional
    override fun trackInteractionEvent(userId: UUID, request: InteractionEventRequest): Mono<Void> {
        logger.debug(
            "Tracking interaction event: userId={}, contentId={}, type={}",
            userId,
            request.contentId,
            request.interactionType
        )

        val contentId = request.contentId!!
        val interactionType = request.interactionType!!

        // 1. 카운터 증가 (content_interactions) - 메인 트랜잭션
        val incrementCounter = when (interactionType) {
            InteractionType.LIKE -> contentInteractionRepository.incrementLikeCount(contentId)
            InteractionType.SAVE -> contentInteractionRepository.incrementSaveCount(contentId)
            InteractionType.SHARE -> contentInteractionRepository.incrementShareCount(contentId)
            InteractionType.COMMENT -> contentInteractionRepository.incrementCommentCount(contentId)
        }

        // 2. 이벤트 발행 (LIKE, SAVE, SHARE만 협업 필터링에 사용)
        return incrementCounter.doOnSuccess {
            if (interactionType != InteractionType.COMMENT) {
                logger.debug(
                    "Publishing UserInteractionEvent: userId={}, contentId={}, type={}",
                    userId,
                    contentId,
                    interactionType
                )
                eventPublisher.publish(
                    UserInteractionEvent(
                        userId = userId,
                        contentId = contentId,
                        interactionType = interactionType
                    )
                )
            }
        }.then()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AnalyticsServiceImpl::class.java)
    }
}
