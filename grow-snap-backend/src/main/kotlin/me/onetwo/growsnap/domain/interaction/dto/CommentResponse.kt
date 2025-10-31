package me.onetwo.growsnap.domain.interaction.dto

/**
 * 댓글 응답 DTO
 *
 * @property id 댓글 ID
 * @property contentId 콘텐츠 ID
 * @property userId 작성자 ID
 * @property userNickname 작성자 닉네임
 * @property userProfileImageUrl 작성자 프로필 이미지 URL
 * @property content 댓글 내용
 * @property parentCommentId 부모 댓글 ID (null이면 최상위 댓글)
 * @property createdAt 작성 시각
 * @property replyCount 대댓글 개수 (0이면 대댓글 없음)
 */
data class CommentResponse(
    val id: String,
    val contentId: String,
    val userId: String,
    val userNickname: String,
    val userProfileImageUrl: String?,
    val content: String,
    val parentCommentId: String?,
    val createdAt: String,
    val replyCount: Int = 0
)
