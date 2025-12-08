package me.onetwo.upvy.domain.interaction.dto

/**
 * 좋아요 수 응답 DTO
 *
 * @property contentId 콘텐츠 ID
 * @property likeCount 좋아요 수
 */
data class LikeCountResponse(
    val contentId: String,
    val likeCount: Int
)
