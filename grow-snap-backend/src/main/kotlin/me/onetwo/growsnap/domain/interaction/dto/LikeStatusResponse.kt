package me.onetwo.growsnap.domain.interaction.dto

import java.util.UUID

/**
 * 좋아요 상태 응답 DTO
 *
 * 특정 콘텐츠에 대한 사용자의 좋아요 상태를 나타냅니다.
 *
 * @property contentId 콘텐츠 ID
 * @property isLiked 좋아요 여부 (true: 좋아요, false: 좋아요 안 함)
 */
data class LikeStatusResponse(
    val contentId: String,
    val isLiked: Boolean
)
