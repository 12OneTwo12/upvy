package me.onetwo.growsnap.domain.interaction.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * 댓글 엔티티
 *
 * 사용자가 콘텐츠에 작성한 댓글을 나타냅니다.
 * 대댓글 지원을 위해 parent_comment_id를 포함합니다.
 *
 * @property id 댓글 ID
 * @property contentId 콘텐츠 ID
 * @property userId 작성자 ID
 * @property parentCommentId 부모 댓글 ID (대댓글인 경우)
 * @property content 댓글 내용
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class Comment(
    val id: UUID? = null,
    val contentId: UUID,
    val userId: UUID,
    val parentCommentId: UUID? = null,
    val content: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: String? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val updatedBy: String? = null,
    val deletedAt: LocalDateTime? = null
)
