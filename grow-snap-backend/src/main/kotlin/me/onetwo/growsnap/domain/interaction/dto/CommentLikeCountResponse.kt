package me.onetwo.growsnap.domain.interaction.dto

/**
 * 댓글 좋아요 수 응답 DTO
 *
 * @property commentId 댓글 ID
 * @property likeCount 좋아요 수
 */
data class CommentLikeCountResponse(
    val commentId: String,
    val likeCount: Int
)
