package me.onetwo.upvy.domain.interaction.event

import java.util.UUID

/**
 * 댓글 생성 이벤트
 *
 * 사용자가 콘텐츠에 댓글을 작성했을 때 발행되는 이벤트입니다.
 *
 * ### 이벤트 처리 흐름
 * 1. CommentService가 comments 테이블에 저장 성공
 * 2. CommentCreatedEvent 발행
 * 3. InteractionEventListener가 비동기로 처리:
 *    - content_interactions의 comment_count 증가
 *    - user_content_interactions 테이블에 저장 (협업 필터링용)
 *
 * @property userId 사용자 ID
 * @property contentId 콘텐츠 ID
 */
data class CommentCreatedEvent(
    val userId: UUID,
    val contentId: UUID
)
