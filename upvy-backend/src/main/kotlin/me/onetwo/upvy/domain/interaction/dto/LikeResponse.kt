package me.onetwo.upvy.domain.interaction.dto

/**
 * 좋아요 응답 DTO
 *
 * @property contentId 콘텐츠 ID
 * @property likeCount 좋아요 수
 * @property isLiked 현재 사용자의 좋아요 여부
 */
data class LikeResponse(
    val contentId: String,
    val likeCount: Int,
    val isLiked: Boolean
)
