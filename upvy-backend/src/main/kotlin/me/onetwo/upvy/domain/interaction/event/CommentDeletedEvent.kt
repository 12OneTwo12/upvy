package me.onetwo.upvy.domain.interaction.event

import java.util.UUID

/**
 * 댓글 삭제 이벤트
 *
 * 사용자가 댓글을 삭제했을 때 발행되는 이벤트입니다.
 *
 * ### 이벤트 처리 흐름
 * 1. CommentService가 comments 테이블에서 삭제 성공 (Soft Delete)
 * 2. CommentDeletedEvent 발행
 * 3. InteractionEventListener가 비동기로 처리:
 *    - content_interactions의 comment_count 감소
 *    - user_content_interactions는 삭제하지 않음 (협업 필터링 데이터 보존)
 *
 * @property userId 사용자 ID
 * @property contentId 콘텐츠 ID
 */
data class CommentDeletedEvent(
    val userId: UUID,
    val contentId: UUID
)
