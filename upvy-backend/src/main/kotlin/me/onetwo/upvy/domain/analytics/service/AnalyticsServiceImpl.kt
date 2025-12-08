package me.onetwo.upvy.domain.analytics.service

import me.onetwo.upvy.domain.analytics.dto.ViewEventRequest
import me.onetwo.upvy.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.upvy.domain.analytics.repository.UserViewHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Analytics 서비스 구현체
 *
 * 사용자 시청 기록 추적
 * - user_view_history 저장
 * - view_count 증가 (skipped가 아닌 경우)
 *
 * @property userViewHistoryRepository 사용자 시청 기록 레포지토리
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 */
@Service
@Transactional(readOnly = true)
class AnalyticsServiceImpl(
    private val userViewHistoryRepository: UserViewHistoryRepository,
    private val contentInteractionRepository: ContentInteractionRepository
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

    companion object {
        private val logger = LoggerFactory.getLogger(AnalyticsServiceImpl::class.java)
    }
}
