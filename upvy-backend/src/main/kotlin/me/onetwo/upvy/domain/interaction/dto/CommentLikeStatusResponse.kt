package me.onetwo.upvy.domain.interaction.dto

/**
 * 댓글 좋아요 상태 응답 DTO
 *
 * 특정 댓글에 대한 사용자의 좋아요 상태를 나타냅니다.
 *
 * @property commentId 댓글 ID
 * @property isLiked 좋아요 여부 (true: 좋아요, false: 좋아요 안 함)
 */
data class CommentLikeStatusResponse(
    val commentId: String,
    val isLiked: Boolean
)
