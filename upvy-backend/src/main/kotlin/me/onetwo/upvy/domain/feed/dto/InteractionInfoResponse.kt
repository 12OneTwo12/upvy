package me.onetwo.upvy.domain.feed.dto

/**
 * 인터랙션 정보 응답 DTO
 *
 * 콘텐츠의 인터랙션 통계 정보 및 사용자별 상태를 포함합니다.
 *
 * @property likeCount 좋아요 수
 * @property commentCount 댓글 수
 * @property saveCount 저장 수
 * @property shareCount 공유 수
 * @property viewCount 조회수
 * @property isLiked 현재 사용자의 좋아요 여부
 * @property isSaved 현재 사용자의 저장 여부
 */
data class InteractionInfoResponse(
    val likeCount: Int,
    val commentCount: Int,
    val saveCount: Int,
    val shareCount: Int,
    val viewCount: Int,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false
)
