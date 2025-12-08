package me.onetwo.upvy.domain.interaction.dto

/**
 * 댓글 좋아요 응답 DTO
 *
 * @property commentId 댓글 ID
 * @property likeCount 좋아요 수
 * @property isLiked 현재 사용자의 좋아요 여부
 */
data class CommentLikeResponse(
    val commentId: String,
    val likeCount: Int,
    val isLiked: Boolean
)
