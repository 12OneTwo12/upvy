package me.onetwo.growsnap.domain.analytics.event

import me.onetwo.growsnap.domain.analytics.service.UserContentInteractionService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 사용자 인터랙션 이벤트 리스너
 *
 * UserInteractionEvent를 비동기로 처리하여
 * user_content_interactions 테이블에 저장합니다.
 *
 * ## Spring Event 패턴 (WebFlux 환경)
 *
 * ### @EventListener의 역할
 * - 이벤트가 발행되면 즉시 리스너가 실행됨
 * - WebFlux 환경에서 @TransactionalEventListener는 동작하지 않음
 *
 * ### @Async의 역할
 * - 이벤트 처리를 별도 스레드에서 비동기로 실행
 * - 메인 요청의 응답 시간에 영향을 주지 않음
 *
 * ### 장점
 * 1. **성능 향상**: 메인 응답 시간 단축 (비동기 처리)
 * 2. **낮은 결합도**: Analytics 로직과 협업 필터링 로직 분리
 * 3. **장애 격리**: user_content_interactions 저장 실패해도 메인 요청 성공
 * 4. **확장성**: 나중에 다른 이벤트 리스너 추가 가능 (예: 알림, 통계)
 *
 * ### 처리 흐름
 * ```
 * 1. 사용자가 좋아요 클릭
 * 2. AnalyticsService.trackInteractionEvent() 호출
 * 3. content_interactions 테이블의 like_count 증가
 * 4. 작업 성공 후 UserInteractionEvent 발행
 * 5. [비동기] UserInteractionEventListener.handleUserInteractionEvent() 실행
 * 6. [비동기] UserContentInteractionService를 통해 user_content_interactions 저장
 * ```
 *
 * ### 에러 처리
 * - user_content_interactions 저장 실패 시 로그만 남기고 예외를 전파하지 않음
 * - 메인 요청은 이미 성공했으므로 사용자에게 에러를 노출하지 않음
 *
 * @property userContentInteractionService 사용자별 콘텐츠 인터랙션 서비스
 */
@Component
class UserInteractionEventListener(
    private val userContentInteractionService: UserContentInteractionService
) {

    /**
     * 사용자 인터랙션 이벤트 처리
     *
     * 비동기로 user_content_interactions 테이블에 저장합니다.
     *
     * ### EventListener
     * - 이벤트 발행 즉시 실행됨
     * - WebFlux 환경에서는 TransactionalEventListener 대신 EventListener 사용
     *
     * ### Async 처리
     * - 별도 스레드에서 실행되어 메인 응답 시간에 영향을 주지 않음
     *
     * @param event 사용자 인터랙션 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleUserInteractionEvent(event: UserInteractionEvent) {
        try {
            logger.debug(
                "Handling UserInteractionEvent: userId={}, contentId={}, type={}",
                event.userId,
                event.contentId,
                event.interactionType
            )

            userContentInteractionService.saveUserInteraction(
                event.userId,
                event.contentId,
                event.interactionType
            )
        } catch (e: Exception) {
            logger.error(
                "Failed to handle UserInteractionEvent: userId={}, contentId={}, type={}",
                event.userId,
                event.contentId,
                event.interactionType,
                e
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserInteractionEventListener::class.java)
    }
}
