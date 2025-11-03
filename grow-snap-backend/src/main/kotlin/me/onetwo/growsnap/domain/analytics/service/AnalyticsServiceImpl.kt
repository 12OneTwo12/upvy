package me.onetwo.growsnap.domain.analytics.service

import me.onetwo.growsnap.domain.analytics.dto.InteractionEventRequest
import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.dto.ViewEventRequest
import me.onetwo.growsnap.domain.analytics.event.UserInteractionEvent
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.analytics.repository.UserViewHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Analytics 서비스 구현체
 *
 * 사용자 행동 추적 비즈니스 로직을 담당합니다.
 * - 시청 기록 저장 및 조회수 증가
 * - 인터랙션 (좋아요, 저장, 공유, 댓글) 추적
 * - Spring Event를 통한 비동기 사용자 인터랙션 기록 (협업 필터링용)
 *
 * ## Spring Event 패턴 사용
 *
 * 사용자 인터랙션(LIKE, SAVE, SHARE)은 Spring Event를 통해 비동기로 처리됩니다.
 * - 메인 트랜잭션: content_interactions 카운터 증가
 * - 이벤트 발행: UserInteractionEvent 발행
 * - 비동기 처리: UserInteractionEventListener가 user_content_interactions 저장
 *
 * @property userViewHistoryRepository 사용자 시청 기록 레포지토리
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 * @property applicationEventPublisher Spring 이벤트 발행자
 */
@Service
@Transactional(readOnly = true)
class AnalyticsServiceImpl(
    private val userViewHistoryRepository: UserViewHistoryRepository,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) : AnalyticsService {

    /**
     * 시청 이벤트 추적
     *
     * ### 처리 흐름
     * 1. 시청 기록을 user_view_history에 저장
     * 2. 스킵이 아닌 경우, content_interactions의 view_count 증가
     *
     * ### 비즈니스 규칙
     * - 스킵 이벤트 (skipped=true): view_count 증가 안 함
     * - 정상 시청 (skipped=false): view_count 증가
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
     * 인터랙션 타입에 따라 해당 카운터를 증가시키고,
     * Spring Event를 통해 비동기적으로 사용자별 인터랙션을 기록합니다.
     *
     * ### 처리 흐름 (Spring Event 패턴)
     * 1. content_interactions 테이블의 카운터 증가 (메인 트랜잭션)
     * 2. LIKE, SAVE, SHARE의 경우: UserInteractionEvent 발행
     * 3. 트랜잭션 커밋
     * 4. UserInteractionEventListener가 비동기로 user_content_interactions 저장
     *
     * ### 인터랙션 타입별 처리
     * - LIKE: like_count 증가 + 이벤트 발행
     * - SAVE: save_count 증가 + 이벤트 발행
     * - SHARE: share_count 증가 + 이벤트 발행
     * - COMMENT: comment_count 증가만 (이벤트 발행 안 함)
     *
     * ### Spring Event 패턴의 장점
     * - 성능 향상: 메인 응답 시간 단축
     * - 낮은 결합도: Analytics와 협업 필터링 로직 분리
     * - 장애 격리: user_content_interactions 저장 실패해도 메인 요청 성공
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
                applicationEventPublisher.publishEvent(
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
