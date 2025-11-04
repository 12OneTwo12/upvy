package me.onetwo.growsnap.domain.interaction.event

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.service.ContentInteractionService
import me.onetwo.growsnap.domain.analytics.service.UserContentInteractionService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * 인터랙션 이벤트 리스너
 *
 * 좋아요/저장/댓글 이벤트를 Reactive하게 처리합니다.
 *
 * ### 처리 흐름 (R2DBC 환경)
 * 1. 이벤트 리스너가 Mono<Void>를 반환하여 완전한 Reactive Chain 구성
 * 2. content_interactions 테이블 카운트 증가/감소
 * 3. user_content_interactions 테이블 저장 (협업 필터링용)
 * 4. 모든 작업이 reactive하게 순차 실행됨
 *
 * ### 장애 격리
 * - 이벤트 리스너 실패해도 메인 트랜잭션에 영향 없음
 * - 로그만 남기고 예외를 삼킴 (onErrorResume)
 *
 * @property contentInteractionService 콘텐츠 인터랙션 서비스
 * @property userContentInteractionService 사용자 콘텐츠 인터랙션 서비스
 */
@Component
class InteractionEventListener(
    private val contentInteractionService: ContentInteractionService,
    private val userContentInteractionService: UserContentInteractionService
) {

    /**
     * 좋아요 생성 이벤트 처리
     *
     * ### 처리 흐름 (Reactive)
     * 1. content_interactions의 like_count 증가
     * 2. user_content_interactions 저장 (협업 필터링용)
     *
     * 비동기 방식으로 실행됩니다. 실패해도 메인 트랜잭션에 영향을 주지 않습니다.
     *
     * @param event 좋아요 생성 이벤트
     */
    @EventListener
    fun handleLikeCreated(event: LikeCreatedEvent) {
        logger.debug(
            "Handling LikeCreatedEvent: userId={}, contentId={}",
            event.userId,
            event.contentId
        )

        // Execute reactively - subscribe triggers async execution
        contentInteractionService.incrementLikeCount(event.contentId)
            .doOnSuccess {
                logger.debug("Like count incremented for contentId={}", event.contentId)
            }
            .doOnError { error ->
                logger.error("Failed to increment like count for contentId={}", event.contentId, error)
            }
            .subscribe()

        userContentInteractionService.saveUserInteraction(
            event.userId,
            event.contentId,
            InteractionType.LIKE
        )
            .doOnSuccess {
                logger.debug("User interaction saved for LikeCreatedEvent")
            }
            .subscribe()
    }

    /**
     * 좋아요 삭제 이벤트 처리
     *
     * ### 처리 흐름 (Reactive)
     * 1. content_interactions의 like_count 감소
     * 2. user_content_interactions는 삭제하지 않음 (협업 필터링 데이터 보존)
     *
     * 비동기 방식으로 실행됩니다.
     *
     * @param event 좋아요 삭제 이벤트
     */
    @EventListener
    fun handleLikeDeleted(event: LikeDeletedEvent) {
        logger.debug(
            "Handling LikeDeletedEvent: userId={}, contentId={}",
            event.userId,
            event.contentId
        )

        contentInteractionService.decrementLikeCount(event.contentId)
            .doOnSuccess {
                logger.debug("Like count decremented for contentId={}", event.contentId)
            }
            .doOnError { error ->
                logger.error("Failed to decrement like count for contentId={}", event.contentId, error)
            }
            .subscribe()
    }

    /**
     * 저장 생성 이벤트 처리
     *
     * ### 처리 흐름 (Reactive)
     * 1. content_interactions의 save_count 증가
     * 2. user_content_interactions 저장 (협업 필터링용)
     *
     * 비동기 방식으로 실행됩니다.
     *
     * @param event 저장 생성 이벤트
     */
    @EventListener
    fun handleSaveCreated(event: SaveCreatedEvent) {
        logger.debug(
            "Handling SaveCreatedEvent: userId={}, contentId={}",
            event.userId,
            event.contentId
        )

        contentInteractionService.incrementSaveCount(event.contentId)
            .doOnSuccess {
                logger.debug("Save count incremented for contentId={}", event.contentId)
            }
            .doOnError { error ->
                logger.error("Failed to increment save count for contentId={}", event.contentId, error)
            }
            .subscribe()

        userContentInteractionService.saveUserInteraction(
            event.userId,
            event.contentId,
            InteractionType.SAVE
        )
            .doOnSuccess {
                logger.debug("User interaction saved for SaveCreatedEvent")
            }
            .subscribe()
    }

    /**
     * 저장 삭제 이벤트 처리
     *
     * ### 처리 흐름 (Reactive)
     * 1. content_interactions의 save_count 감소
     * 2. user_content_interactions는 삭제하지 않음 (협업 필터링 데이터 보존)
     *
     * 비동기 방식으로 실행됩니다.
     *
     * @param event 저장 삭제 이벤트
     */
    @EventListener
    fun handleSaveDeleted(event: SaveDeletedEvent) {
        logger.debug(
            "Handling SaveDeletedEvent: userId={}, contentId={}",
            event.userId,
            event.contentId
        )

        contentInteractionService.decrementSaveCount(event.contentId)
            .doOnSuccess {
                logger.debug("Save count decremented for contentId={}", event.contentId)
            }
            .doOnError { error ->
                logger.error("Failed to decrement save count for contentId={}", event.contentId, error)
            }
            .subscribe()
    }

    /**
     * 댓글 생성 이벤트 처리
     *
     * ### 처리 흐름 (Reactive)
     * 1. content_interactions의 comment_count 증가
     * 2. user_content_interactions 저장 (협업 필터링용)
     *
     * 비동기 방식으로 실행됩니다.
     *
     * @param event 댓글 생성 이벤트
     */
    @EventListener
    fun handleCommentCreated(event: CommentCreatedEvent) {
        logger.debug(
            "Handling CommentCreatedEvent: userId={}, contentId={}",
            event.userId,
            event.contentId
        )

        contentInteractionService.incrementCommentCount(event.contentId)
            .doOnSuccess {
                logger.debug("Comment count incremented for contentId={}", event.contentId)
            }
            .doOnError { error ->
                logger.error("Failed to increment comment count for contentId={}", event.contentId, error)
            }
            .subscribe()

        userContentInteractionService.saveUserInteraction(
            event.userId,
            event.contentId,
            InteractionType.COMMENT
        )
            .doOnSuccess {
                logger.debug("User interaction saved for CommentCreatedEvent")
            }
            .subscribe()
    }

    /**
     * 댓글 삭제 이벤트 처리
     *
     * ### 처리 흐름 (Reactive)
     * 1. content_interactions의 comment_count 감소
     * 2. user_content_interactions는 삭제하지 않음 (협업 필터링 데이터 보존)
     *
     * 비동기 방식으로 실행됩니다.
     *
     * @param event 댓글 삭제 이벤트
     */
    @EventListener
    fun handleCommentDeleted(event: CommentDeletedEvent) {
        logger.debug(
            "Handling CommentDeletedEvent: userId={}, contentId={}",
            event.userId,
            event.contentId
        )

        contentInteractionService.decrementCommentCount(event.contentId)
            .doOnSuccess {
                logger.debug("Comment count decremented for contentId={}", event.contentId)
            }
            .doOnError { error ->
                logger.error("Failed to decrement comment count for contentId={}", event.contentId, error)
            }
            .subscribe()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InteractionEventListener::class.java)
    }
}
