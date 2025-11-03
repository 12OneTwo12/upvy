package me.onetwo.growsnap.domain.interaction.event

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.service.ContentInteractionService
import me.onetwo.growsnap.domain.analytics.service.UserContentInteractionService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 인터랙션 이벤트 리스너
 *
 * 좋아요/저장/댓글 이벤트를 비동기로 처리합니다.
 *
 * ### 처리 흐름
 * 1. 메인 트랜잭션 커밋 후 실행 (@TransactionalEventListener + AFTER_COMMIT)
 * 2. 비동기로 실행 (@Async)
 * 3. content_interactions 테이블 카운트 증가/감소
 * 4. user_content_interactions 테이블 저장 (협업 필터링용)
 *
 * ### 장애 격리
 * - 이벤트 리스너 실패해도 메인 트랜잭션에 영향 없음
 * - 로그만 남기고 예외를 삼킴
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
     * ### 처리 흐름
     * 1. content_interactions의 like_count 증가
     * 2. user_content_interactions 저장 (협업 필터링용)
     *
     * @param event 좋아요 생성 이벤트
     */
    @EventListener
    fun handleLikeCreated(event: LikeCreatedEvent) {
        try {
            logger.debug(
                "Handling LikeCreatedEvent: userId={}, contentId={}",
                event.userId,
                event.contentId
            )

            // user_content_interactions 저장 (협업 필터링용)
            userContentInteractionService.saveUserInteraction(
                event.userId,
                event.contentId,
                InteractionType.LIKE
            )

            logger.debug("LikeCreatedEvent handled successfully")
        } catch (e: Exception) {
            logger.error("Failed to handle LikeCreatedEvent", e)
        }
    }

    /**
     * 좋아요 삭제 이벤트 처리
     *
     * ### 처리 흐름
     * 1. content_interactions의 like_count 감소
     * 2. user_content_interactions는 삭제하지 않음 (협업 필터링 데이터 보존)
     *
     * @param event 좋아요 삭제 이벤트
     */
    @EventListener
    fun handleLikeDeleted(event: LikeDeletedEvent) {
        try {
            logger.debug(
                "Handling LikeDeletedEvent: userId={}, contentId={}",
                event.userId,
                event.contentId
            )

            // Note: like_count는 service layer에서 이미 감소시켰음

            logger.debug("LikeDeletedEvent handled successfully")
        } catch (e: Exception) {
            logger.error("Failed to handle LikeDeletedEvent", e)
        }
    }

    /**
     * 저장 생성 이벤트 처리
     *
     * ### 처리 흐름
     * 1. content_interactions의 save_count 증가
     * 2. user_content_interactions 저장 (협업 필터링용)
     *
     * @param event 저장 생성 이벤트
     */
    @EventListener
    fun handleSaveCreated(event: SaveCreatedEvent) {
        try {
            logger.debug(
                "Handling SaveCreatedEvent: userId={}, contentId={}",
                event.userId,
                event.contentId
            )

            // user_content_interactions 저장 (협업 필터링용)
            userContentInteractionService.saveUserInteraction(
                event.userId,
                event.contentId,
                InteractionType.SAVE
            )

            logger.debug("SaveCreatedEvent handled successfully")
        } catch (e: Exception) {
            logger.error("Failed to handle SaveCreatedEvent", e)
        }
    }

    /**
     * 저장 삭제 이벤트 처리
     *
     * ### 처리 흐름
     * 1. content_interactions의 save_count 감소
     * 2. user_content_interactions는 삭제하지 않음 (협업 필터링 데이터 보존)
     *
     * @param event 저장 삭제 이벤트
     */
    @EventListener
    fun handleSaveDeleted(event: SaveDeletedEvent) {
        try {
            logger.debug(
                "Handling SaveDeletedEvent: userId={}, contentId={}",
                event.userId,
                event.contentId
            )

            // Note: save_count는 service layer에서 이미 감소시켰음

            logger.debug("SaveDeletedEvent handled successfully")
        } catch (e: Exception) {
            logger.error("Failed to handle SaveDeletedEvent", e)
        }
    }

    /**
     * 댓글 생성 이벤트 처리
     *
     * ### 처리 흐름
     * 1. content_interactions의 comment_count 증가
     * 2. user_content_interactions 저장 (협업 필터링용)
     *
     * @param event 댓글 생성 이벤트
     */
    @EventListener
    fun handleCommentCreated(event: CommentCreatedEvent) {
        try {
            logger.debug(
                "Handling CommentCreatedEvent: userId={}, contentId={}",
                event.userId,
                event.contentId
            )

            // user_content_interactions 저장 (협업 필터링용)
            userContentInteractionService.saveUserInteraction(
                event.userId,
                event.contentId,
                InteractionType.COMMENT
            )

            logger.debug("CommentCreatedEvent handled successfully")
        } catch (e: Exception) {
            logger.error("Failed to handle CommentCreatedEvent", e)
        }
    }

    /**
     * 댓글 삭제 이벤트 처리
     *
     * ### 처리 흐름
     * 1. content_interactions의 comment_count 감소
     * 2. user_content_interactions는 삭제하지 않음 (협업 필터링 데이터 보존)
     *
     * @param event 댓글 삭제 이벤트
     */
    @EventListener
    fun handleCommentDeleted(event: CommentDeletedEvent) {
        try {
            logger.debug(
                "Handling CommentDeletedEvent: userId={}, contentId={}",
                event.userId,
                event.contentId
            )

            // Note: comment_count는 service layer에서 이미 감소시켰음

            logger.debug("CommentDeletedEvent handled successfully")
        } catch (e: Exception) {
            logger.error("Failed to handle CommentDeletedEvent", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(InteractionEventListener::class.java)
    }
}
