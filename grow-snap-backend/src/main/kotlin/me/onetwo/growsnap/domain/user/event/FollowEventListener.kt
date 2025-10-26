package me.onetwo.growsnap.domain.user.event

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 팔로우 이벤트 리스너
 *
 * 팔로우 이벤트를 비동기로 처리하여 알림을 생성합니다.
 *
 * ## 처리 흐름
 * 1. 메인 트랜잭션 커밋 후 실행 (@TransactionalEventListener + AFTER_COMMIT)
 * 2. 비동기로 실행 (@Async)
 * 3. 팔로우 알림 생성 및 전송
 *
 * ## 장애 격리
 * - 이 메서드가 실패해도 메인 트랜잭션(팔로우 요청)에 영향 없음
 * - 로그만 남기고 예외를 삼킴
 * - 알림 전송 실패는 사용자 경험에 크리티컬하지 않음
 *
 * ## 향후 확장
 * - 현재는 로그만 남김 (MVP)
 * - 추후 알림 테이블 저장, 푸시 알림 전송, 이메일 발송 등 추가 예정
 */
@Component
class FollowEventListener {

    /**
     * 팔로우 이벤트 처리
     *
     * 팔로우된 사용자에게 알림을 전송합니다.
     * 현재는 로그만 남기며, 추후 실제 알림 시스템과 통합 예정입니다.
     *
     * @param event 팔로우 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleFollowEvent(event: FollowEvent) {
        try {
            logger.debug(
                "Handling FollowEvent: follower={}, following={}, timestamp={}",
                event.followerId,
                event.followingId,
                event.timestamp
            )

            // TODO: 실제 알림 시스템과 통합
            // 1. notifications 테이블에 알림 저장
            // 2. 푸시 알림 전송 (FCM)
            // 3. 이메일 알림 (선택적)

            logger.info(
                "Follow notification created: {} followed {}",
                event.followerId,
                event.followingId
            )

            logger.debug("FollowEvent handled successfully")
        } catch (e: Exception) {
            // 예외를 삼켜서 메인 트랜잭션에 영향을 주지 않음
            logger.error(
                "Failed to handle FollowEvent: follower={}, following={}",
                event.followerId,
                event.followingId,
                e
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FollowEventListener::class.java)
    }
}
